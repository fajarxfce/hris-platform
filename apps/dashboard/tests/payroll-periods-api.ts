import { expect, type Page } from "@playwright/test";
import type {
  PayrollPeriodCreationDto,
  PayrollPeriodDetailsDto,
} from "../src/features/payroll/data/models/payroll-period-dto";
import {
  periodActor,
  periodDetails,
  periodEmployee,
  periodId,
  periodRecord,
} from "./fixtures/payroll-periods";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export const periodKey = (company: string, id: string) => `${company}:${id}`;
export async function installPayrollPeriodsApi(page: Page, permissions = ["payroll.calculate"]) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const employees = Array.from({ length: 53 }, (_, i) => periodEmployee(i + 1));
  const initial = periodDetails({ participantCount: 52 });
  initial.members = {
    items: employees.slice(0, 52).map((row) => ({
      employeeId: row.id,
      employeeNumber: row.employeeNumber,
      name: row.name,
      inputVersion: null,
      inputStatus: null,
    })),
    nextCursor: null,
  };
  const records = new Map<string, PayrollPeriodDetailsDto>([
    [periodKey(companyIds[0], periodId), initial],
  ]);
  const reads: URL[] = [],
    writes: { company: string; id: string; operation: string; body: Record<string, unknown> }[] =
      [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let failure: string | null = null,
    drop = false,
    held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  function cancel(company: string, id: string, reason: string) {
    const record = records.get(periodKey(company, id));
    if (!record) throw new Error("Missing period fixture");
    record.period = { ...record.period, status: "CANCELLED", version: record.period.version + 1 };
    record.history.items.push({
      version: record.period.version,
      status: "CANCELLED",
      actorId: periodActor,
      recordedAt: "2026-10-14T01:00:00Z",
      reason,
      runId: null,
    });
    return { id, version: record.period.version };
  }
  await page.route("**/api/v1/companies/*/payroll/periods**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4] ?? "",
      id = url.pathname.split("/")[7] ?? "";
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    if (request.method() === "GET") {
      reads.push(url);
      const wait = held;
      if (wait) {
        held = null;
        wait.entered.resolve();
        await wait.released.promise;
      }
      if (failure) {
        const code = failure;
        failure = null;
        return route.fulfill({ status: 403, json: { code, fields: {}, parameters: {} } });
      }
      if (id === "employees") {
        const query = (url.searchParams.get("query") ?? "").toLowerCase(),
          after = url.searchParams.get("after");
        const filtered = (
          company === companyIds[0]
            ? employees
            : [{ ...periodEmployee(54), name: "South Payroll Employee" }]
        ).filter(
          (row) =>
            (row.name.toLowerCase().includes(query) ||
              row.employeeNumber.toLowerCase().includes(query)) &&
            (after === null || row.employeeNumber > after),
        );
        return route.fulfill({
          json: {
            items: filtered.slice(0, 50),
            nextCursor: filtered.length > 50 ? filtered[49]?.employeeNumber : null,
          },
        });
      }
      if (!id) {
        const from = url.searchParams.get("from") ?? "",
          until = url.searchParams.get("until") ?? "",
          status = url.searchParams.get("status"),
          after = url.searchParams.get("after");
        const items = [...records.entries()]
          .filter(([key]) => key.startsWith(`${company}:`))
          .map(([, value]) => value.period)
          .filter(
            (row) =>
              row.earningsMonth >= from &&
              row.earningsMonth <= until &&
              (status === null || row.status === status) &&
              (after === null || row.id > after),
          )
          .sort((a, b) => a.id.localeCompare(b.id));
        return route.fulfill({
          json: { items: items.slice(0, 20), nextCursor: items.length > 20 ? items[19]?.id : null },
        });
      }
      const record = records.get(periodKey(company, id));
      if (!record)
        return route.fulfill({ status: 404, json: { code: "payroll_period_not_found" } });
      const after = url.searchParams.get("after"),
        historyAfter = Number(url.searchParams.get("historyAfter") ?? -1);
      const members = record.members.items.filter(
          (row) => after === null || row.employeeId > after,
        ),
        history = record.history.items.filter((row) => row.version > historyAfter);
      return route.fulfill({
        json: {
          period: record.period,
          members: {
            items: members.slice(0, 50),
            nextCursor: members.length > 50 ? members[49]?.employeeId : null,
          },
          history: {
            items: history.slice(0, 20),
            nextCursor: history.length > 20 ? String(history[19]?.version) : null,
          },
        },
      });
    }
    expect(request.method()).toBe("POST");
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as Record<string, unknown>;
    writes.push({ company, id, operation, body });
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    if (!permissions.includes("payroll.calculate"))
      return route.fulfill({ status: 403, json: { code: "access_denied" } });
    if (failure) {
      const code = failure;
      failure = null;
      return route.fulfill({ status: 409, json: { code } });
    }
    const previous = receipts.get(`${company}:${operation}`);
    if (previous) {
      expect(JSON.stringify(body)).toBe(previous.payload);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    let receipt: { id: string; version: number };
    if (id) {
      const record = records.get(periodKey(company, id));
      if (!record)
        return route.fulfill({ status: 404, json: { code: "payroll_period_not_found" } });
      if (record.period.version !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      if (record.period.status !== "DRAFT")
        return route.fulfill({ status: 409, json: { code: "payroll_period_not_draft" } });
      receipt = cancel(company, id, String(body.reason));
    } else {
      const input = body as unknown as PayrollPeriodCreationDto;
      if (
        [...records.entries()].some(
          ([key, value]) =>
            key.startsWith(`${company}:`) &&
            value.period.earningsMonth === input.earningsMonth &&
            value.period.status !== "CANCELLED",
        )
      )
        return route.fulfill({ status: 409, json: { code: "payroll_period_active" } });
      const record = periodDetails();
      record.period = periodRecord({
        id: input.id,
        earningsMonth: input.earningsMonth,
        plannedPaymentMonth: input.plannedPaymentDate.slice(0, 7),
        plannedPaymentDate: input.plannedPaymentDate,
        participantCount: input.employeeIds.length,
      });
      record.members.items = input.employeeIds.map((id) => {
        const employee = employees.find((row) => row.id === id);
        if (!employee) throw new Error("Unacquired fixture employee");
        return {
          employeeId: id,
          employeeNumber: employee.employeeNumber,
          name: employee.name,
          inputVersion: null,
          inputStatus: null,
        };
      });
      if (record.history.items[0]) record.history.items[0].reason = input.reason;
      records.set(periodKey(company, input.id), record);
      receipt = { id: input.id, version: 0 };
    }
    receipts.set(`${company}:${operation}`, { ...receipt, payload: JSON.stringify(body) });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    reads,
    writes,
    records,
    failNext: (code: string) => {
      failure = code;
    },
    dropNext: () => {
      drop = true;
    },
    cancel: (id = periodId) => cancel(companyIds[0], id, "Concurrent cancellation"),
    holdRead: () => {
      const entered = gate(),
        released = gate();
      held = { entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
