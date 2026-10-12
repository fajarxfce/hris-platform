import { expect, type Page } from "@playwright/test";
import type { WorkforceEmployeeDto } from "../src/features/workforce/data/models/calendar-dto";
import type { OvertimePlanDto } from "../src/features/workforce/data/models/overtime-plan-dto";
import { identifier, overtimeDtoFixture } from "../src/features/workforce/di/overtime-fixture";
import { overtimePlanningDtoFixture } from "../src/features/workforce/di/overtime-planning-fixture";
import { companyIds } from "./identity-api";
import { installOvertimeApi } from "./overtime-api";

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

/** Owned transport fixture; the separate PostgreSQL scenario verifies business transactions. */
export async function installOvertimePlanningApi(
  page: Page,
  permissions = ["overtime.read", "overtime.manage"],
) {
  const base = await installOvertimeApi(page, permissions);
  const employees = new Map<string, WorkforceEmployeeDto[]>(
    companyIds.map((company, index) => [
      company,
      Array.from({ length: index === 0 ? 51 : 1 }, (_, position) => ({
        id: identifier(20_000 + index * 1000 + position + 1),
        employeeNumber: `${index === 0 ? "NORTH" : "SOUTH"}-${String(position + 1).padStart(3, "0")}`,
        legalName: `${index === 0 ? "North" : "South"} Employee ${position + 1}`,
        status: "ACTIVE",
        version: 3,
      })),
    ]),
  );
  const reads: URL[] = [],
    writes: { company: string; operation: string; body: OvertimePlanDto }[] = [];
  const receipts = new Map<string, { payload: string; id: string }>();
  let revision = "a".repeat(64),
    commits = 0,
    lose = false;
  let rejected: { code: string; status: number } | null = null,
    readFailure: string | null = null;
  let heldRead: { kind: "employees" | "review"; gate: ReturnType<typeof gate> } | null = null;
  let heldWrite: ReturnType<typeof gate> | null = null;
  await page.route(
    "**/api/v1/companies/*/workforce/overtime/planning/employees**",
    async (route) => {
      const url = new URL(route.request().url()),
        parts = url.pathname.split("/");
      const company = parts[4] ?? "",
        employee = parts[9],
        workDate = url.searchParams.get("workDate") ?? "";
      reads.push(url);
      const all = employees.get(company) ?? [],
        failure = readFailure;
      readFailure = null;
      let body: object | null;
      if (employee) {
        const found = all.find((value) => value.id === employee),
          review = overtimePlanningDtoFixture();
        body = found
          ? {
              ...review,
              employee: found,
              workDate,
              schedule: { ...review.schedule, workDate },
              revision,
            }
          : null;
      } else {
        expect(url.searchParams.get("limit")).toBe("50");
        const query = (url.searchParams.get("query") ?? "").toLowerCase(),
          after = url.searchParams.get("after");
        const selected = all.filter(
          (value) =>
            `${value.legalName} ${value.employeeNumber}`.toLowerCase().includes(query) &&
            (after === null || value.employeeNumber > after),
        );
        const items = selected.slice(0, 50);
        body = { items, nextCursor: selected.length > 50 ? items.at(-1)?.employeeNumber : null };
      }
      const snapshot = structuredClone(body);
      if (heldRead?.kind === (employee ? "review" : "employees")) {
        const held = heldRead.gate;
        heldRead = null;
        held.enter();
        await held.held;
      }
      return route.fulfill(
        failure
          ? { status: 403, json: { code: failure, detail: "PRIVATE DETAIL" } }
          : snapshot
            ? { json: snapshot }
            : { status: 404, json: { code: "employee_not_found" } },
      );
    },
  );
  await page.route("**/api/v1/companies/*/workforce/overtime", async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const company = new URL(route.request().url()).pathname.split("/")[4] ?? "";
    const operation = route.request().headers()["idempotency-key"] ?? "",
      body = route.request().postDataJSON() as OvertimePlanDto;
    writes.push({ company, operation, body });
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
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
    const payload = JSON.stringify({ company, body }),
      receipt = receipts.get(operation);
    if (receipt)
      return receipt.payload === payload
        ? route.fulfill({ json: { id: receipt.id, version: 0 } })
        : route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (body.planningRevision !== revision)
      return route.fulfill({ status: 409, json: { code: "overtime_planning_changed" } });
    const employee = employees.get(company)?.find((value) => value.id === body.employeeId);
    if (!employee) return route.fulfill({ status: 404, json: { code: "employee_not_found" } });
    const record = overtimeDtoFixture("PLANNED");
    const companyRequests = base.records.get(company);
    companyRequests?.push({
      ...record,
      id: body.id,
      employeeId: body.employeeId,
      employeeName: employee.legalName,
      employeeNumber: employee.employeeNumber,
      workDate: body.workDate,
      reason: body.reason,
      schedule: { ...record.schedule, workDate: body.workDate },
      requested: {
        ...body.requested,
        workedMinutes:
          (Date.parse(body.requested.endsAt) - Date.parse(body.requested.startsAt)) / 60_000 -
          body.requested.breakMinutes,
      },
    });
    receipts.set(operation, { payload, id: body.id });
    commits += 1;
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id: body.id, version: 0 } });
  });
  return {
    ...base,
    planningReads: reads,
    writes,
    get commits() {
      return commits;
    },
    reviseSchedule: () => {
      revision = "b".repeat(64);
    },
    loseNext: () => {
      lose = true;
    },
    rejectWrite: (code: string, status = 409) => {
      rejected = { code, status };
    },
    rejectRead: (code: string) => {
      readFailure = code;
    },
    holdRead: (kind: "employees" | "review") => {
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
