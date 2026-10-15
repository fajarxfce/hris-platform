import { expect, type Page } from "@playwright/test";
import type { LeaveCarryExpiryDetailsDto } from "../src/features/leave/data/models/leave-carry-expiry-dto";
import { batchEmployee, deferred } from "../src/features/leave/di/leave-batch-fixture";
import { carryWire } from "../src/features/leave/di/leave-carry-expiry-fixture";
import { companyIds } from "./identity-api";
import { batchPermissions, installLeaveBatchesApi } from "./leave-batches-api";

type ReadKind = "list" | "detail";
export async function installLeaveCarryExpiryApi(page: Page, permissions = batchPermissions) {
  const batches = await installLeaveBatchesApi(page, permissions);
  const records = new Map<string, LeaveCarryExpiryDetailsDto[]>(companyIds.map((id) => [id, []]));
  const reads: { kind: ReadKind; url: URL }[] = [];
  const writes: {
    company: string;
    id: string;
    operation: string;
    body: Record<string, unknown>;
  }[] = [];
  const receipts = new Map<
    string,
    { body: string; id: string; value: { id: string; version: number } }
  >();
  let lost = false,
    committed = 0,
    delivered = 0;
  let commandFailure: { code: string; status: number } | null = null;
  let readFailure: { kind: ReadKind; code: string; status: number } | null = null;
  type Gate = {
    kind: ReadKind | "write";
    entered: ReturnType<typeof deferred<void>>;
    released: ReturnType<typeof deferred<void>>;
  };
  let gate: Gate | null = null;
  function seed(company: string = companyIds[0], value = structuredClone(carryWire)) {
    records.get(company)?.push(value);
    return value;
  }
  function complete(value: LeaveCarryExpiryDetailsDto) {
    for (
      let ordinal = value.expiry.completedEmployees + 1;
      ordinal <= value.expiry.totalEmployees;
      ordinal++
    ) {
      const employeeId = batchEmployee(ordinal);
      value.results.items.push({
        ordinal,
        employeeId,
        jobId: value.job.id,
        status: "NO_AWARD",
        expiredDays: "0",
        ledgerId: null,
        recordedAt: "2027-04-01T00:01:01Z",
      });
      value.employees.push({ id: employeeId, name: "Example Colleague", number: "EMP-003" });
    }
    value.expiry.status = "SUCCEEDED";
    value.expiry.completedEmployees = value.expiry.totalEmployees;
    value.expiry.failureCode = null;
    value.job = {
      ...value.job,
      status: "SUCCEEDED",
      completedItems: value.job.totalItems,
      version: value.job.version + 2,
      attempts: 1,
      finishedAt: "2027-04-01T00:01:02Z",
      failureCode: null,
    };
    value.availableActions = [];
  }
  await page.route(
    /\/api\/v1\/companies\/[^/]+\/leave\/carry-expiries(?:[/?].*)?$/,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        parts = url.pathname.split("/");
      const company = parts[4] ?? "",
        id = parts[7];
      const reject = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, fields: {}, parameters: {}, detail: "PRIVATE EXPIRY DIAGNOSTIC" },
        });
      const all = records.get(company);
      if (!all) return reject("company_access_denied", 403);
      if (request.method() === "GET") {
        const kind: ReadKind = id ? "detail" : "list";
        reads.push({ kind, url });
        expect(url.searchParams.get("limit")).toBe("20");
        const failure = readFailure?.kind === kind ? readFailure : null;
        if (failure) {
          readFailure = null;
          return reject(failure.code, failure.status);
        }
        let payload: unknown;
        if (id) {
          const value = all.find((item) => item.expiry.id === id);
          if (!value) return reject("leave_carry_expiry_not_found", 404);
          const after = Number(url.searchParams.get("after") ?? "0");
          const outcomes = value.results.items.filter((item) => item.ordinal > after);
          const selected = outcomes.slice(0, 20);
          payload = {
            ...value,
            results: {
              items: selected,
              nextCursor: outcomes.length > 20 ? String(selected.at(-1)?.ordinal) : null,
            },
            employees: value.employees.filter((employee) =>
              selected.some((item) => item.employeeId === employee.id),
            ),
            availableActions: permissions.includes("leave.year.close")
              ? value.availableActions
              : [],
          };
        } else {
          const after = url.searchParams.get("after");
          const items = all
            .map((item) => item.expiry)
            .sort((a, b) => a.id.localeCompare(b.id))
            .filter((item) => after === null || item.id > after);
          payload = {
            items: items.slice(0, 20),
            nextCursor: items.length > 20 ? items[19]?.id : null,
          };
        }
        const snapshot = structuredClone(payload);
        if (gate?.kind === kind) {
          const held = gate;
          gate = null;
          held.entered.resolve();
          await held.released.promise;
        }
        delivered++;
        return route.fulfill({ json: snapshot });
      }
      expect(request.method()).toBe("POST");
      expect(parts[8]).toBe("resume");
      const body = request.postDataJSON() as Record<string, unknown>;
      const operation = request.headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
      expect(request.headers()["x-csrf-token"]).toBeTruthy();
      expect(Object.keys(body).sort()).toEqual(["expectedVersion", "reason"]);
      writes.push({ company, id: id ?? "", operation, body });
      if (gate?.kind === "write") {
        const held = gate;
        gate = null;
        held.entered.resolve();
        await held.released.promise;
      }
      if (commandFailure) {
        const failure = commandFailure;
        commandFailure = null;
        return reject(failure.code, failure.status);
      }
      const key = `${company}:${operation}`,
        receipt = receipts.get(key);
      if (receipt) {
        expect(receipt.id).toBe(id);
        expect(receipt.body).toBe(JSON.stringify(body));
        return route.fulfill({ json: receipt.value });
      }
      const value = all.find((item) => item.expiry.id === id);
      if (!value) return reject("leave_carry_expiry_not_found", 404);
      if (value.expiry.version !== body.expectedVersion) return reject("stale_version");
      if (!["FAILED", "CANCELLED"].includes(value.job.status))
        return reject("leave_carry_expiry_not_stopped");
      const jobId = crypto.randomUUID(),
        now = "2027-04-01T00:01:00Z";
      value.expiry = {
        ...value.expiry,
        version: value.expiry.version + 1,
        jobId,
        status: "QUEUED",
        failureCode: null,
      };
      value.attempts.push({
        jobId,
        number: value.expiry.version + 1,
        baseCompleted: value.expiry.completedEmployees,
        startedAt: now,
        reason: String(body.reason),
      });
      value.job = {
        id: jobId,
        status: "QUEUED",
        completedItems: 0,
        totalItems: value.expiry.totalEmployees - value.expiry.completedEmployees + 1,
        attempts: 0,
        version: 0,
        failureCode: null,
        scheduledFor: now,
        availableAt: now,
        finishedAt: null,
      };
      value.availableActions = [];
      const result = { id: value.expiry.id, version: value.expiry.version };
      receipts.set(key, { id: result.id, body: JSON.stringify(body), value: result });
      committed++;
      if (lost) {
        lost = false;
        return route.abort("connectionfailed");
      }
      return route.fulfill({ json: result });
    },
  );
  return {
    batches,
    identity: batches.identity,
    records,
    reads,
    writes,
    seed,
    complete,
    get committed() {
      return committed;
    },
    get delivered() {
      return delivered;
    },
    loseCommand: () => {
      lost = true;
    },
    rejectCommand: (code: string, status = 409) => {
      commandFailure = { code, status };
    },
    rejectRead: (kind: ReadKind, code: string, status = 403) => {
      readFailure = { kind, code, status };
    },
    hold: (kind: ReadKind | "write") => {
      const held: Gate = { kind, entered: deferred<void>(), released: deferred<void>() };
      gate = held;
      return { entered: held.entered.promise, release: () => held.released.resolve() };
    },
  };
}
