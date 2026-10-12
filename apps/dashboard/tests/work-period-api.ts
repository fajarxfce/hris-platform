import { expect, type Page } from "@playwright/test";
import type { WorkforceEmployeeDto } from "../src/features/workforce/data/models/calendar-dto";
import type { WorkPeriodCommandDto } from "../src/features/workforce/data/models/work-period-command-dto";
import type { WorkPeriodOverviewDto } from "../src/features/workforce/data/models/work-period-overview-dto";
import {
  identifier,
  periodMonth,
  workPeriodOverviewDtoFixture,
  workPeriodSnapshotDtoFixture,
} from "../src/features/workforce/di/work-period-fixture";
import type { WorkPeriodStatus } from "../src/features/workforce/domain/entities/work-period";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let release!: () => void, enter!: () => void;
  return {
    held: new Promise<void>((done) => {
      release = done;
    }),
    started: new Promise<void>((done) => {
      enter = done;
    }),
    release: () => release(),
    enter: () => enter(),
  };
}
type ReadKind = "overview" | "employees" | "snapshot";

/** Transport fixture only. A separate real-worker test verifies publication and stored facts. */
export async function installWorkPeriodApi(
  page: Page,
  status: WorkPeriodStatus = "OPEN",
  permissions = ["workforce.read", "workforce.close"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const periods = new Map<string, WorkPeriodOverviewDto>();
  const employees = new Map<string, WorkforceEmployeeDto[]>(
    companyIds.map((company, index) => [
      company,
      Array.from({ length: index === 0 ? 51 : 1 }, (_, position) => ({
        id: identifier(21_000 + index * 1000 + position + 1),
        employeeNumber: `${index === 0 ? "NORTH" : "SOUTH"}-${String(position + 1).padStart(3, "0")}`,
        legalName: `${index === 0 ? "North" : "South"} Employee ${position + 1}`,
        status: "ACTIVE",
        version: 3,
      })),
    ]),
  );
  const reads: { kind: ReadKind; company: string; url: URL }[] = [];
  const writes: {
    company: string;
    intent: string;
    operation: string;
    body: WorkPeriodCommandDto;
  }[] = [];
  const receipts = new Map<string, { payload: string; id: string; version: number }>();
  let commits = 0,
    lose = false;
  let rejected: { code: string; status: number } | null = null;
  let readFailure: { kind: ReadKind; code: string; status: number } | null = null;
  let heldRead: { kind: ReadKind; gate: ReturnType<typeof gate> } | null = null;
  let heldWrite: ReturnType<typeof gate> | null = null;
  function period(company: string, month: string) {
    const key = `${company}:${month}`;
    let value = periods.get(key);
    if (!value) {
      value = workPeriodOverviewDtoFixture(status);
      value.month = month;
      if (value.period) value.period.month = month;
      periods.set(key, value);
    }
    return value;
  }

  await page.route("**/api/v1/companies/*/workforce/periods/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url());
    const parts = url.pathname.split("/"),
      company = parts[4] ?? "",
      month = parts[7] ?? "",
      action = parts[8],
      employeeId = parts[9];
    const value = period(company, month);
    if (request.method() === "GET") {
      const kind: ReadKind =
        action === "employees" ? (employeeId ? "snapshot" : "employees") : "overview";
      reads.push({ kind, company, url });
      const all = employees.get(company) ?? [];
      let body: object;
      if (kind === "overview") {
        body = {
          ...value,
          availableActions: value.availableActions.filter((action) =>
            action === "view_snapshots"
              ? permissions.includes("workforce.read")
              : action === "close"
                ? permissions.includes("workforce.close") && month < value.companyDate.slice(0, 7)
                : action === "recover"
                  ? permissions.includes("workforce.close")
                  : true,
          ),
        };
      } else if (kind === "employees") {
        expect(url.searchParams.get("limit")).toBe("50");
        const query = (url.searchParams.get("query") ?? "").toLowerCase(),
          after = url.searchParams.get("after");
        const matches = all.filter(
          (item) =>
            `${item.legalName} ${item.employeeNumber}`.toLowerCase().includes(query) &&
            (after === null || item.employeeNumber > after),
        );
        const items = matches.slice(0, 50);
        body = { items, nextCursor: matches.length > 50 ? items.at(-1)?.employeeNumber : null };
      } else {
        const employee = all.find((item) => item.id === employeeId);
        expect(employee).toBeDefined();
        const snapshot = workPeriodSnapshotDtoFixture();
        body = { ...snapshot, employeeId, employee, period: value.period };
      }
      const snapshot = structuredClone(body),
        failure = readFailure?.kind === kind ? readFailure : null;
      if (failure) readFailure = null;
      if (heldRead?.kind === kind) {
        const held = heldRead.gate;
        heldRead = null;
        held.enter();
        await held.held;
      }
      return route.fulfill(
        failure
          ? { status: failure.status, json: { code: failure.code, detail: "PRIVATE DETAIL" } }
          : { json: snapshot },
      );
    }
    if (request.method() !== "POST" || (action !== "close" && action !== "recover"))
      return route.fallback();
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as WorkPeriodCommandDto;
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    writes.push({ company, intent: action, operation, body });
    if (heldWrite) {
      const held = heldWrite;
      heldWrite = null;
      held.enter();
      await held.held;
    }
    if (rejected) {
      const failure = rejected;
      rejected = null;
      return route.fulfill({
        status: failure.status,
        json: { code: failure.code, detail: "PRIVATE DETAIL" },
      });
    }
    const payload = JSON.stringify({ company, month, action, body }),
      receipt = receipts.get(operation);
    if (receipt)
      return receipt.payload === payload
        ? route.fulfill({ json: { id: receipt.id, version: receipt.version } })
        : route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (body.expectedVersion !== (value.period?.version ?? 0))
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    const next = workPeriodOverviewDtoFixture(
      action === "close" ? "PROCESSING" : "REVIEW_REQUIRED",
    );
    if (!next.period || !next.attempt) throw new Error("Invalid period fixture");
    next.period.version = body.expectedVersion + 1;
    if (value.period) next.period.id = value.period.id;
    next.period.month = month;
    if (action === "close") {
      next.period.jobId = identifier(30_000 + commits);
      next.attempt.id = next.period.jobId;
    } else if (value.attempt) {
      next.attempt = structuredClone(value.attempt);
      next.period.jobId = value.attempt.id;
      next.period.failureCode = value.attempt.failureCode ?? "job_failed";
    }
    next.month = month;
    periods.set(`${company}:${month}`, next);
    const result = {
      id: action === "close" ? next.attempt.id : next.period.id,
      version: next.period.version,
    };
    receipts.set(operation, { payload, ...result });
    commits += 1;
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: result });
  });
  return {
    identity,
    reads,
    writes,
    employees,
    get commits() {
      return commits;
    },
    setStatus: (status: WorkPeriodStatus, company: string = companyIds[0]) =>
      periods.set(`${company}:${periodMonth}`, workPeriodOverviewDtoFixture(status)),
    stopAttempt: () => {
      const value = workPeriodOverviewDtoFixture("PROCESSING");
      if (value.attempt) {
        value.attempt.status = "FAILED";
        value.attempt.finishedAt = "2026-10-12T01:00:10Z";
        value.attempt.failureCode = "job_failed";
      }
      value.availableActions = ["recover", "view_job"];
      periods.set(`${companyIds[0]}:${periodMonth}`, value);
    },
    loseNext: () => {
      lose = true;
    },
    rejectWrite: (code: string, status = 409) => {
      rejected = { code, status };
    },
    rejectRead: (kind: ReadKind, code: string, status = 403) => {
      readFailure = { kind, code, status };
    },
    holdRead: (kind: ReadKind) => {
      const held = gate();
      heldRead = { kind, gate: held };
      return held;
    },
    holdWrite: () => {
      const held = gate();
      heldWrite = held;
      return held;
    },
  };
}
