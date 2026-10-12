import { afterEach, describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { WorkPeriodId } from "../domain/entities/work-period";
import { closedAttendanceTotals } from "../domain/policies/closed-attendance-totals";
import { workPeriodDates } from "../domain/policies/work-period-policy";
import { employeeDtoFixture } from "./calendar-fixture";
import {
  company,
  employeeId,
  identifier,
  periodAccess,
  periodId,
  periodJobId,
  periodMonth,
  workPeriodContextFixture,
  workPeriodOverviewDtoFixture,
  workPeriodSnapshotDtoFixture,
} from "./work-period-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const signal = () => new AbortController().signal;
const operation = identifier(899) as OperationId;
function fixture() {
  const raw = workPeriodOverviewDtoFixture("CLOSED"),
    snapshot = workPeriodSnapshotDtoFixture();
  const request = vi.fn<HttpClient["request"]>().mockImplementation(async (input) => {
    if (input.path.endsWith("/recover")) return { id: periodId, version: 2 };
    if (input.method === "POST") return { id: periodJobId, version: 1 };
    if (input.path.includes(`/employees/${employeeId}`)) return snapshot;
    if (input.path.includes("/employees?"))
      return { items: [employeeDtoFixture()], nextCursor: null };
    return raw;
  });
  const feature = createWorkforceFeature({ request });
  return {
    raw,
    snapshot,
    request,
    feature,
    overview: (abort = signal()) =>
      feature.loadWorkPeriodOverview.execute(periodAccess, periodMonth, abort),
    details: (abort = signal()) =>
      feature.loadWorkPeriodSnapshot.execute(periodAccess, periodMonth, employeeId, abort),
    close: (abort = signal()) =>
      feature.closeWorkPeriod.execute(
        periodAccess,
        operation,
        workPeriodContextFixture(),
        "  Monthly review  ",
        abort,
      ),
    recover: (abort = signal()) =>
      feature.recoverWorkPeriod.execute(
        periodAccess,
        operation,
        workPeriodContextFixture("recover"),
        "  Stopped attempt reviewed  ",
        abort,
      ),
  };
}
afterEach(() => vi.useRealTimers());

describe("monthly closing reads", () => {
  it("acquires coherent metadata, normalizes scope and does not retain mutable DTOs", async () => {
    const f = fixture();
    const response = await f.overview();
    expect(response).toMatchObject({
      ok: true,
      value: {
        companyId: company,
        month: periodMonth,
        period: { companyId: company, status: "CLOSED" },
        attempt: { totalEmployees: 10, completedEmployees: 10 },
      },
    });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/workforce/periods/${periodMonth}`,
    });
    if (!response.ok) return;
    f.raw.availableActions.length = 0;
    if (f.raw.attempt) f.raw.attempt.completedEmployees = 0;
    expect(response.value.availableActions).toEqual(["view_job", "view_snapshots"]);
    expect(response.value.attempt?.completedEmployees).toBe(10);
    expect(Object.isFrozen(response.value.attempt)).toBe(true);
    expect(Object.isFrozen(response.value.period)).toBe(true);
  });

  it("keeps a month without a stored period explicit and ignores additive unknown actions", async () => {
    const f = fixture();
    Object.assign(f.raw, {
      period: null,
      attempt: null,
      availableActions: ["close", "future_action"],
    });
    expect(await f.overview()).toMatchObject({
      ok: true,
      value: { period: null, attempt: null, availableActions: ["close"] },
    });
  });

  it("separates closing metadata, published employee discovery and owned snapshot authority", async () => {
    const f = fixture(),
      closeOnly = { ...periodAccess, permissions: ["workforce.close"] };
    expect(
      await f.feature.loadWorkPeriodEmployees.execute(
        closeOnly,
        { month: periodMonth, query: "", after: null },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.feature.loadWorkPeriodSnapshot.execute(closeOnly, periodMonth, employeeId, signal()),
    ).toMatchObject({ ok: false });
    expect(
      await f.feature.closeWorkPeriod.execute(
        { ...periodAccess, permissions: ["workforce.read"] },
        operation,
        workPeriodContextFixture(),
        "Review",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
    expect(
      await f.feature.loadWorkPeriodOverview.execute(closeOnly, periodMonth, signal()),
    ).toMatchObject({ ok: true });
    for (const permission of ["workforce.team.read", "attendance.self.record"]) {
      const access = { ...periodAccess, permissions: [permission] };
      expect(
        await f.feature.loadWorkPeriodOverview.execute(access, periodMonth, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await f.feature.loadWorkPeriodSnapshot.execute(access, periodMonth, employeeId, signal()),
      ).toMatchObject({ ok: true });
    }
    expect(f.request).toHaveBeenCalledTimes(3);
  });

  it.each(["1999-12", "2101-01", "2026-00", "2026-13", "2026-9", "../jobs"])(
    "rejects invalid month %s before I/O",
    async (month) => {
      const f = fixture();
      expect(
        await f.feature.loadWorkPeriodOverview.execute(periodAccess, month, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_work_period_range" } });
      expect(
        await f.feature.loadWorkPeriodSnapshot.execute(periodAccess, month, employeeId, signal()),
      ).toMatchObject({ ok: false });
      expect(f.request).not.toHaveBeenCalled();
    },
  );

  it("uses bounded encoded employee search and rejects non-progressing pages", async () => {
    const f = fixture();
    expect(
      await f.feature.loadWorkPeriodEmployees.execute(
        periodAccess,
        { month: periodMonth, query: "  Calendar & Co  ", after: "EMP-000" },
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { items: [{ id: employeeId, companyId: company }] } });
    const url = new URL(f.request.mock.calls[0]?.[0].path ?? "", "https://example.test");
    expect(url.pathname).toBe(
      `/api/v1/companies/${company}/workforce/periods/${periodMonth}/employees`,
    );
    expect(Object.fromEntries(url.searchParams)).toEqual({
      query: "Calendar & Co",
      limit: "50",
      after: "EMP-000",
    });
    f.request.mockResolvedValue({
      items: [employeeDtoFixture(), employeeDtoFixture()],
      nextCursor: null,
    });
    expect(
      await f.feature.loadWorkPeriodEmployees.execute(
        periodAccess,
        { month: periodMonth, query: "", after: null },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("rejects oversized filters, invalid employees and cursors without a request", async () => {
    const f = fixture();
    for (const values of [
      { query: "x".repeat(121), after: null },
      { query: "", after: "" },
      { query: "", after: "../" },
    ])
      expect(
        await f.feature.loadWorkPeriodEmployees.execute(
          periodAccess,
          { month: periodMonth, ...values },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(
      await f.feature.loadWorkPeriodSnapshot.execute(
        periodAccess,
        periodMonth,
        "invalid",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
    expect(f.request).not.toHaveBeenCalled();
  });

  it.each([
    "month",
    "timezone",
    "companyDate",
    "job",
    "count",
    "status",
    "time",
    "actions",
  ] as const)("rejects inconsistent overview %s", async (field) => {
    const f = fixture();
    if (field === "month") f.raw.month = "2026-08";
    if (field === "timezone") f.raw.companyTimezone = "Invalid/Zone";
    if (field === "companyDate") f.raw.companyDate = "2026-02-30";
    if (field === "job" && f.raw.attempt) f.raw.attempt.id = identifier(991);
    if (field === "count" && f.raw.attempt) f.raw.attempt.completedEmployees = 11;
    if (field === "status" && f.raw.attempt) f.raw.attempt.status = "RUNNING";
    if (field === "time" && f.raw.period) f.raw.period.closedAt = "2026-01-01T00:00:00Z";
    if (field === "actions") f.raw.availableActions = ["close"];
    expect(await f.overview()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
});

describe("published attendance facts", () => {
  it("retains the month and historical calendar, and keeps missing attendance distinct from absence", async () => {
    const f = fixture();
    const response = await f.details();
    expect(response.ok).toBe(true);
    if (!response.ok) return;
    expect(response.value.days).toHaveLength(30);
    expect(closedAttendanceTotals(response.value.days)).toEqual({
      acceptedMinutes: 450,
      overtimeMinutes: 135,
      facts: { WORKED: 1, ABSENCE_RECORDED: 1, OFF: 1, UNASSIGNED: 1, UNRECORDED: 26 },
    });
    const first = f.snapshot.days[0];
    if (first) first.acceptedMinutes = 1;
    expect(response.value.days[0]?.acceptedMinutes).toBe(450);
    expect(Object.isFrozen(response.value.days)).toBe(true);
    expect(Object.isFrozen(response.value.days[2]?.overtime)).toBe(true);
    expect(response.value.employee.companyId).toBe(company);
    expect(workPeriodDates("2024-02")).toHaveLength(29);
    expect(workPeriodDates("2025-02")).toHaveLength(28);
    expect(workPeriodDates("bad")).toEqual([]);
  });

  it.each([
    "employee",
    "month",
    "partial",
    "date",
    "fact",
    "correction",
    "overtime",
    "period",
  ] as const)("rejects malformed snapshot %s", async (field) => {
    const f = fixture(),
      first = f.snapshot.days[0],
      second = f.snapshot.days[1],
      request = f.snapshot.days[2]?.overtime[0];
    if (field === "employee") f.snapshot.employeeId = identifier(991);
    if (field === "month") f.snapshot.month = "2026-08";
    if (field === "partial") f.snapshot.days.pop();
    if (field === "date" && first) first.workDate = "2026-09-02";
    if (field === "fact" && first) first.fact = "UNRECORDED";
    if (field === "correction" && first && second) second.correctionId = first.correctionId;
    if (field === "overtime" && request) request.approvedMinutes = 136;
    if (field === "period") f.snapshot.period.status = "PROCESSING";
    expect(await f.details()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
});

describe("closing and recovery commands", () => {
  it("preserves the reviewed version, reason and operation across a later-date retry without a read", async () => {
    const f = fixture();
    expect(await f.close()).toEqual({ ok: true, value: { id: periodJobId, version: 1 } });
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2028-01-01T00:00:00Z"));
    expect(await f.close()).toEqual({ ok: true, value: { id: periodJobId, version: 1 } });
    expect(f.request.mock.calls.map(([input]) => input)).toEqual(
      Array(2).fill({
        path: `/api/v1/companies/${company}/workforce/periods/${periodMonth}/close`,
        method: "POST",
        operationId: operation,
        body: { expectedVersion: 0, reason: "Monthly review" },
      }),
    );
  });

  it("recovery uses its own observed version and exact period receipt", async () => {
    const f = fixture();
    expect(await f.recover()).toEqual({ ok: true, value: { id: periodId, version: 2 } });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/workforce/periods/${periodMonth}/recover`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 1, reason: "Stopped attempt reviewed" },
    });
    f.request.mockResolvedValue({ id: periodJobId, version: 2 });
    expect(await f.recover()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    f.request.mockResolvedValue({ id: periodJobId, version: 2 });
    expect(await f.close()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("rejects mismatched scope, period, observed version and reason before I/O", async () => {
    const f = fixture(),
      context = workPeriodContextFixture();
    for (const changed of [
      { ...context, companyId: identifier(88) as CompanyId },
      { ...context, version: -1 },
      { ...context, version: Number.MAX_SAFE_INTEGER },
      { ...context, periodId: "bad" as WorkPeriodId },
      { ...context, companyDate: "2026-02-30" },
      { ...context, companyDate: "2026-09-30" },
    ])
      expect(
        await f.feature.closeWorkPeriod.execute(
          periodAccess,
          operation,
          changed,
          "Review",
          signal(),
        ),
      ).toMatchObject({ ok: false });
    for (const reason of ["   ", "x".repeat(1001)])
      expect(
        await f.feature.closeWorkPeriod.execute(periodAccess, operation, context, reason, signal()),
      ).toMatchObject({ ok: false });
    expect(
      await f.feature.recoverWorkPeriod.execute(
        periodAccess,
        operation,
        context,
        "Review",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
  });

  it("preserves API failure codes and propagates cancellation before and during I/O", async () => {
    const f = fixture();
    f.request.mockRejectedValue(
      new HttpResponseError(403, { code: "mfa_required", fields: {}, parameters: {} }),
    );
    expect(await f.close()).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => f.overview(cancelled.signal)).toThrow();
    const active = new AbortController();
    f.request.mockImplementation(
      (_input, abort) =>
        new Promise((_resolve, reject) => {
          abort.addEventListener("abort", () => reject(abort.reason), { once: true });
        }),
    );
    const pending = f.details(active.signal);
    active.abort();
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
  });
});
