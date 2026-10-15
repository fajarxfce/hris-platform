import { expect, type Page } from "@playwright/test";
import type { LeaveBatchDetailsDto } from "../src/features/leave/data/models/leave-batch-dto";
import {
  batchEmployee,
  batchId,
  batchReviewWire,
  batchWire,
} from "../src/features/leave/di/leave-batch-fixture";
import { companyIds, installIdentityApi } from "./identity-api";
export const batchPermissions = ["leave.read", "leave.accrual.post", "leave.year.close"];
export { batchId };

function gate() {
  let enter!: () => void, release!: () => void;
  const entered = new Promise<void>((done) => {
      enter = done;
    }),
    held = new Promise<void>((done) => {
      release = done;
    });
  return { enter, release, entered, held };
}
export async function installLeaveBatchesApi(page: Page, permissions = batchPermissions) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, LeaveBatchDetailsDto[]>(companyIds.map((id) => [id, []]));
  const types = Array.from({ length: 22 }, (_, index) => ({
    ...structuredClone(batchReviewWire.type),
    id:
      index === 0
        ? batchReviewWire.type.id
        : `80000000-abcd-4000-8000-${String(index + 1).padStart(12, "0")}`,
    code: index === 0 ? "ANNUAL" : `BATCH_${String(index + 1).padStart(3, "0")}`,
    name: index === 0 ? "Annual leave" : `Leave type ${index + 1}`,
    appliedRevision: 4,
  }));
  const reads: URL[] = [],
    writes: {
      company: string;
      action: string;
      operation: string;
      body: Record<string, unknown>;
    }[] = [],
    ledgerReads: URL[] = [];
  const receipts = new Map<
    string,
    { body: string; action: string; value: { id: string; version: number } }
  >();
  let lose = false,
    committed = 0,
    readFailure: string | null = null,
    commandFailure: { code: string; status: number } | null = null;
  let readGate: ReturnType<typeof gate> | null = null,
    writeGate: ReturnType<typeof gate> | null = null;
  const seed = (): LeaveBatchDetailsDto => structuredClone(batchWire);
  function complete(item: LeaveBatchDetailsDto, count = item.batch.totalEmployees) {
    const original = item.results.items[0] ?? batchWire.results.items[0];
    if (!original) throw Error("Missing outcome fixture");
    item.batch.totalEmployees = count;
    item.batch.status = "COMPLETED";
    item.results = {
      items: Array.from({ length: count }, (_, i) => ({
        ...structuredClone(original),
        ordinal: i + 1,
        employeeId: batchEmployee(i + 1),
        status: i === 1 ? ("SKIPPED" as const) : ("APPLIED" as const),
        jobId: item.batch.jobId,
        failureCode: i === 1 ? "leave_employee_ineligible" : null,
        resourceId: i === 1 ? null : original.resourceId,
      })),
      nextCursor: null,
    };
    item.employees = item.results.items.map((result, i) => ({
      id: result.employeeId,
      name: i === 0 ? "Alex Employee" : i === 1 ? "Robin Employee" : `Employee ${i + 1}`,
      number: `EMP-${String(i + 1).padStart(3, "0")}`,
    }));
    item.counts = {
      applied: count - (count > 1 ? 1 : 0),
      unchanged: 0,
      skipped: count > 1 ? 1 : 0,
      failed: 0,
      completed: count,
    };
    const base = item.attempts.at(-1)?.baseCompleted ?? 0;
    item.job = {
      ...item.job,
      status: "SUCCEEDED",
      completedItems: count - base + 1,
      totalItems: count - base + 1,
      failureCode: null,
    };
    item.availableActions = [];
  }
  await page.route(
    /\/api\/v1\/companies\/[^/]+\/leave\/(?:batches|accrual-batches|year-close-batches)(?:[/?].*)?$/u,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        parts = url.pathname.split("/"),
        company = parts[4] ?? "",
        resource = parts[6],
        id = parts[7],
        action = parts[8] ?? resource ?? "";
      const reject = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, fields: {}, parameters: {}, detail: "PRIVATE BATCH DIAGNOSTIC" },
        });
      const all = records.get(company);
      if (!all) return reject("company_access_denied", 403);
      if (request.method() === "GET") {
        reads.push(url);
        if (readFailure) return reject(readFailure, 403);
        let payload: unknown;
        if (id === "types") {
          expect(url.searchParams.get("limit")).toBe("20");
          const after = url.searchParams.get("after");
          const items = types.filter((type) => after === null || type.code > after);
          payload = {
            items: items.slice(0, 20),
            nextCursor: items.length > 20 ? items[19]?.code : null,
          };
        } else if (id === "review") {
          const type = types.find((type) => type.id === url.searchParams.get("typeId"));
          if (!type) return reject("leave_type_not_found", 404);
          payload = {
            ...structuredClone(batchReviewWire),
            kind: url.searchParams.get("kind"),
            period: url.searchParams.get("period"),
            type: {
              ...type,
              effectiveFrom: `${url.searchParams.get("period")?.slice(0, 4)}-01-01`,
              appliedRevision: 2,
            },
          };
        } else if (id) {
          const item = all.find((item) => item.batch.id === id);
          if (!item) return reject("leave_batch_not_found", 404);
          expect(url.searchParams.get("limit")).toBe("20");
          const after = Number(url.searchParams.get("after") ?? "0"),
            results = item.results.items.filter((row) => row.ordinal > after),
            selected = results.slice(0, 20);
          payload = {
            ...item,
            results: {
              items: selected,
              nextCursor: results.length > 20 ? String(selected.at(-1)?.ordinal) : null,
            },
            employees: item.employees.filter((employee) =>
              selected.some((row) => row.employeeId === employee.id),
            ),
          };
        } else {
          expect(url.searchParams.get("limit")).toBe("20");
          const after = url.searchParams.get("after"),
            items = all
              .map((item) => item.batch)
              .sort((a, b) => a.id.localeCompare(b.id))
              .filter((item) => after === null || item.id > after);
          payload = {
            items: items.slice(0, 20),
            nextCursor: items.length > 20 ? items[19]?.id : null,
          };
        }
        const snapshot = structuredClone(payload),
          held = readGate;
        readGate = null;
        if (held) {
          held.enter();
          await held.held;
        }
        return route.fulfill({ json: snapshot });
      }
      expect(request.method()).toBe("POST");
      const body = request.postDataJSON() as Record<string, unknown>,
        operation = request.headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
      expect(request.headers()["x-csrf-token"]).toBeTruthy();
      expect(Object.keys(body).sort()).toEqual(
        action === "resume"
          ? ["expectedVersion", "reason"]
          : [
              "employeeIds",
              "expectedPolicyVersion",
              "id",
              action === "accrual-batches" ? "month" : "year",
              "reason",
              "typeId",
            ].sort(),
      );
      writes.push({ company, action, operation, body });
      const held = writeGate;
      writeGate = null;
      if (held) {
        held.enter();
        await held.held;
      }
      if (commandFailure) {
        const failure = commandFailure;
        commandFailure = null;
        return reject(failure.code, failure.status);
      }
      const replay = receipts.get(operation);
      if (replay) {
        expect(JSON.stringify(body)).toBe(replay.body);
        expect(action).toBe(replay.action);
        return route.fulfill({ json: replay.value });
      }
      let item: LeaveBatchDetailsDto;
      if (action === "resume") {
        const found = all.find((item) => item.batch.id === id);
        if (!found) return reject("leave_batch_not_found", 404);
        item = found;
        if (item.batch.version !== body.expectedVersion) return reject("stale_version");
        if (!item.availableActions.includes("RESUME")) return reject("leave_batch_not_stopped");
        const jobId = `60000000-0000-4000-8000-${String(item.attempts.length + 1).padStart(12, "0")}`;
        item.batch = { ...item.batch, version: item.batch.version + 1, jobId, status: "RUNNING" };
        item.attempts.push({
          jobId,
          number: item.attempts.length + 1,
          baseCompleted: item.counts.completed,
          startedAt: "2026-10-15T02:00:00Z",
          reason: String(body.reason),
        });
        item.job = {
          jobId,
          status: "QUEUED",
          completedItems: 0,
          totalItems: item.batch.totalEmployees - item.counts.completed + 1,
          attempts: 0,
          version: 0,
          cancellationRequested: false,
          failureCode: null,
        };
        item.availableActions = [];
      } else {
        const type = types.find((type) => type.id === body.typeId);
        if (!type) return reject("leave_type_not_found", 404);
        if (type.version !== body.expectedPolicyVersion)
          return reject("leave_batch_policy_changed");
        expect(body.employeeIds).toEqual(batchReviewWire.employeeIds);
        item = seed();
        item.batch = {
          ...item.batch,
          id: String(body.id),
          kind: action === "accrual-batches" ? "ACCRUAL" : "YEAR_CLOSE",
          typeId: type.id,
          period: action === "accrual-batches" ? String(body.month) : `${body.year}-12`,
          reason: String(body.reason),
          policyVersion: type.version,
          policy: { ...item.batch.policy, typeId: type.id, code: type.code, name: type.name },
        };
        const attempt = item.attempts[0];
        if (!attempt) throw Error("Missing attempt fixture");
        item.attempts[0] = { ...attempt, reason: String(body.reason) };
        complete(item);
        all.push(item);
      }
      committed++;
      const value = { id: item.batch.id, version: item.batch.version };
      receipts.set(operation, { body: JSON.stringify(body), action, value });
      if (lose) {
        lose = false;
        return route.abort("connectionfailed");
      }
      return route.fulfill({ json: value });
    },
  );
  await page.route(
    /\/api\/v1\/companies\/[^/]+\/leave\/employees\/[^/]+\/balances\/[^/]+\/\d{4}(?:\?.*)?$/u,
    async (route) => {
      const url = new URL(route.request().url());
      ledgerReads.push(url);
      return route.fulfill({
        json: {
          employee: batchWire.employees[0],
          typeId: batchReviewWire.type.id,
          typeCode: "ANNUAL",
          typeName: "Annual leave",
          balance: {
            accountId: null,
            year: Number(url.pathname.split("/").at(-1)),
            availableDays: "1",
            reservedDays: "0",
            consumedDays: "0",
            version: 1,
            closed: false,
          },
          entries: { items: [], nextCursor: null },
        },
      });
    },
  );
  return {
    identity,
    records,
    types,
    reads,
    writes,
    ledgerReads,
    seed,
    complete,
    get committed() {
      return committed;
    },
    rejectReads: (code: string | null) => {
      readFailure = code;
    },
    rejectCommand: (code: string, status = 409) => {
      commandFailure = { code, status };
    },
    loseCommand: () => {
      lose = true;
    },
    holdRead: () => {
      const held = gate();
      readGate = held;
      return held;
    },
    holdCommand: () => {
      const held = gate();
      writeGate = held;
      return held;
    },
  };
}
