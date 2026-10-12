import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { overtimePlanContext } from "../domain/entities/overtime-plan-context";
import type { OvertimePlanningSearch } from "../domain/entities/overtime-planning-search";
import {
  isOvertimePlanningDate,
  prepareOvertimePlan,
} from "../domain/policies/overtime-planning-policy";
import { employeeDtoFixture } from "./calendar-fixture";
import {
  company,
  employeeId,
  identifier,
  overtimeAccess,
  overtimeDate,
  overtimeId,
} from "./overtime-fixture";
import { overtimePlanningDtoFixture, overtimePlanningFixture } from "./overtime-planning-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const signal = () => new AbortController().signal;
const operation = identifier(90) as OperationId;
const requested = {
  startsAt: "2026-10-12T07:00:00Z",
  endsAt: "2026-10-12T10:00:00Z",
  breakMinutes: 15,
};
const search: OvertimePlanningSearch = { workDate: overtimeDate, query: "", after: null };
function fixture() {
  const raw = overtimePlanningDtoFixture();
  const request = vi.fn<HttpClient["request"]>().mockImplementation(async (input) => {
    if (input.method === "POST") return { id: overtimeId, version: 0 };
    return input.path.includes(`/employees/${employeeId}`)
      ? raw
      : { items: [employeeDtoFixture()], nextCursor: null };
  });
  const feature = createWorkforceFeature({ request });
  return {
    raw,
    request,
    feature,
    employees: (input = search, abort = signal()) =>
      feature.loadOvertimePlanningEmployees.execute(overtimeAccess, input, abort),
    review: (abort = signal()) =>
      feature.reviewOvertimePlan.execute(overtimeAccess, employeeId, overtimeDate, abort),
    submit: (abort = signal()) =>
      feature.planOvertimeRequest.execute(
        overtimeAccess,
        operation,
        overtimeId,
        overtimePlanContext(overtimePlanningFixture()),
        requested,
        "  Inventory work  ",
        abort,
      ),
  };
}

describe("overtime planning acquisition", () => {
  it("uses scoped discovery with trimmed search, a bounded page and observed employment versions", async () => {
    const f = fixture();
    const result = await f.employees({ ...search, query: "  Calendar & Co  ", after: "EMP-000" });
    expect(result).toMatchObject({
      ok: true,
      value: { items: [{ id: employeeId, companyId: company, version: 3 }], nextCursor: null },
    });
    const url = new URL(f.request.mock.calls[0]?.[0].path ?? "", "https://example.test");
    expect(url.pathname).toBe(`/api/v1/companies/${company}/workforce/overtime/planning/employees`);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      workDate: overtimeDate,
      query: "Calendar & Co",
      after: "EMP-000",
      limit: "50",
    });
    if (result.ok) {
      expect(Object.isFrozen(result.value.items)).toBe(true);
      expect(Object.isFrozen(result.value.items[0])).toBe(true);
    }
  });

  it("separates administrative discovery from owned review and unrelated profile/calendar grants", async () => {
    const f = fixture();
    for (const permissions of [
      ["overtime.read"],
      ["workforce.manage", "workforce.read", "people.read"],
      ["overtime.self.manage"],
    ]) {
      expect(
        await f.feature.loadOvertimePlanningEmployees.execute(
          { ...overtimeAccess, permissions },
          search,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    expect(f.request).not.toHaveBeenCalled();
    expect(
      await f.feature.reviewOvertimePlan.execute(
        { ...overtimeAccess, permissions: ["overtime.self.manage"] },
        employeeId,
        overtimeDate,
        signal(),
      ),
    ).toMatchObject({ ok: true });
    expect(
      await f.feature.reviewOvertimePlan.execute(
        { ...overtimeAccess, permissions: ["overtime.read"] },
        employeeId,
        overtimeDate,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).toHaveBeenCalledOnce();
  });

  it.each([
    { ...search, workDate: "2026-02-30" },
    { ...search, workDate: "1999-12-31" },
    { ...search, workDate: "2101-01-01" },
    { ...search, query: "x".repeat(121) },
    { ...search, after: "../other" },
    { ...search, after: "" },
  ])("rejects invalid planning filters before acquisition: %j", async (input) => {
    const f = fixture();
    expect(await f.employees(input)).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
  });

  it("rejects a non-progressing discovery page", async () => {
    const f = fixture();
    f.request.mockResolvedValue({
      items: [employeeDtoFixture(), employeeDtoFixture()],
      nextCursor: null,
    });
    expect(await f.employees()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("maps a reviewed calendar without retaining mutable DTOs", async () => {
    const f = fixture();
    const result = await f.review();
    expect(result).toMatchObject({ ok: true });
    if (!result.ok) return;
    expect(result.value.employee.version).toBe(3);
    expect(Object.isFrozen(result.value.schedule)).toBe(true);
    const context = overtimePlanContext(result.value);
    f.raw.employee.legalName = "Replacement";
    f.raw.schedule.originVersion = 3;
    expect(result.value.employee.legalName).toBe("Calendar Employee");
    expect(context).not.toHaveProperty("employee");
    expect(context).not.toHaveProperty("legalName");
    expect(context.schedule).toEqual({ kind: "OFF" });
    expect(Object.isFrozen(context)).toBe(true);
    expect(Object.isFrozen(context.schedule)).toBe(true);
  });

  it.each(["employee", "date", "companyDate", "timezone", "revision", "schedule"] as const)(
    "rejects inconsistent review %s",
    async (field) => {
      const f = fixture();
      if (field === "employee") f.raw.employee.id = identifier(999);
      if (field === "date") f.raw.workDate = "2026-10-11";
      if (field === "companyDate") f.raw.companyDate = "2026-12-01";
      if (field === "timezone") f.raw.timezone = "Invalid/Zone";
      if (field === "revision") f.raw.revision = "unbounded revision";
      if (field === "schedule") f.raw.schedule.kind = "UNASSIGNED";
      expect(await f.review()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    },
  );
});

describe("overtime plan command", () => {
  it("sends exact reviewed facts and replays without re-reading current employment or calendar", async () => {
    const f = fixture();
    expect(await f.submit()).toEqual({ ok: true, value: { id: overtimeId, version: 0 } });
    expect(await f.submit()).toEqual({ ok: true, value: { id: overtimeId, version: 0 } });
    const first = f.request.mock.calls[0]?.[0];
    expect(first).toEqual({
      path: `/api/v1/companies/${company}/workforce/overtime`,
      method: "POST",
      operationId: operation,
      body: {
        id: overtimeId,
        employeeId,
        expectedEmploymentVersion: 3,
        workDate: overtimeDate,
        requested,
        reason: "Inventory work",
        planningRevision: "a".repeat(64),
      },
    });
    expect(f.request.mock.calls[1]?.[0]).toEqual(first);
    expect(f.request.mock.calls.every(([input]) => input.method === "POST")).toBe(true);
  });

  it.each([
    { id: identifier(99), version: 0 },
    { id: overtimeId, version: 1 },
    { id: overtimeId, version: -1 },
  ])("rejects an unrelated or noninitial receipt: %j", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(await f.submit()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("validates scope, identifiers, observed versions, reasons and content revisions before I/O", async () => {
    const f = fixture(),
      context = overtimePlanContext(overtimePlanningFixture());
    for (const review of [
      { ...context, companyId: identifier(8) as CompanyId },
      { ...context, employmentVersion: -1 },
      { ...context, employmentVersion: Number.MAX_SAFE_INTEGER + 1 },
      { ...context, revision: "" },
      { ...context, workDate: "2026-08-01" },
    ])
      expect(
        await f.feature.planOvertimeRequest.execute(
          overtimeAccess,
          operation,
          overtimeId,
          review,
          requested,
          "Work",
          signal(),
        ),
      ).toMatchObject({ ok: false });
    for (const reason of ["", "  ", "x".repeat(1001)])
      expect(
        await f.feature.planOvertimeRequest.execute(
          overtimeAccess,
          operation,
          overtimeId,
          context,
          requested,
          reason,
          signal(),
        ),
      ).toMatchObject({ ok: false });
    expect(
      await f.feature.planOvertimeRequest.execute(
        { ...overtimeAccess, permissions: ["overtime.read"] },
        operation,
        overtimeId,
        context,
        requested,
        "Work",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.feature.planOvertimeRequest.execute(
        overtimeAccess,
        "invalid" as OperationId,
        overtimeId,
        context,
        requested,
        "Work",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
  });

  it("keeps API failures localizable without raw diagnostics and cancellation owns late responses", async () => {
    const f = fixture();
    f.request.mockRejectedValueOnce(
      new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }),
    );
    expect(await f.submit()).toEqual({
      ok: false,
      failure: { code: "mfa_required", fields: {}, parameters: {} },
    });
    let complete!: (value: unknown) => void;
    f.request.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    );
    const abort = new AbortController();
    const pending = f.review(abort.signal);
    abort.abort();
    complete(f.raw);
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    const calls = f.request.mock.calls.length;
    expect(() => f.submit(abort.signal)).toThrowError(
      expect.objectContaining({ name: "AbortError" }),
    );
    expect(f.request).toHaveBeenCalledTimes(calls);
  });
});

describe("requested overtime interval policy", () => {
  const context = overtimePlanContext(overtimePlanningFixture());
  const prepare = (input = requested, review = context) =>
    prepareOvertimePlan(overtimeAccess, operation, overtimeId, review, input, "Work");
  it.each([
    { ...requested, startsAt: "2026-10-12T07:00:00.000000001Z" },
    { ...requested, endsAt: "2026-10-12T07:00:00Z" },
    { ...requested, endsAt: "2026-10-12T19:01:00Z" },
    { ...requested, breakMinutes: 121 },
    { ...requested, breakMinutes: -1 },
    { ...requested, breakMinutes: 0.5 },
    { ...requested, endsAt: "2026-10-12T07:15:00Z" },
  ])("rejects invalid elapsed time and precision: %j", (input) =>
    expect(prepare(input)).toMatchObject({
      ok: false,
      failure: { code: "invalid_overtime_interval" },
    }),
  );

  it("compares whole company dates and allows overnight work within the original work date", () => {
    expect(
      prepare({ startsAt: "2026-10-12T16:00:00Z", endsAt: "2026-10-12T20:00:00Z", breakMinutes: 0 })
        .ok,
    ).toBe(true);
    expect(
      prepare({ ...requested, startsAt: "2026-10-12T17:00:00Z", endsAt: "2026-10-12T18:00:00Z" }),
    ).toMatchObject({ ok: false, failure: { code: "overtime_work_date_mismatch" } });
    expect(
      prepare({
        startsAt: "2026-10-11T16:00:00Z",
        endsAt: "2026-10-11T18:00:00Z",
        breakMinutes: 0,
      }),
    ).toMatchObject({ ok: false, failure: { code: "overtime_work_date_mismatch" } });
  });

  it("excludes regular shift time and accepts the exclusive next-day endpoint", () => {
    const work = {
      ...context,
      schedule: {
        kind: "WORK" as const,
        startsAt: "2026-10-12T01:00:00Z",
        endsAt: "2026-10-12T09:00:00Z",
      },
    };
    expect(prepare(requested, work)).toMatchObject({
      ok: false,
      failure: { code: "overtime_overlaps_shift" },
    });
    expect(
      prepare(
        { startsAt: "2026-10-13T15:00:00Z", endsAt: "2026-10-13T17:00:00Z", breakMinutes: 0 },
        work,
      ).ok,
    ).toBe(true);
    expect(
      prepare(
        { startsAt: "2026-10-13T15:00:00Z", endsAt: "2026-10-13T17:01:00Z", breakMinutes: 0 },
        work,
      ),
    ).toMatchObject({ ok: false, failure: { code: "overtime_work_date_mismatch" } });
  });

  it("handles daylight-saving dates using elapsed instants and the reviewed company clock", () => {
    expect(
      prepare(
        { startsAt: "2026-11-01T05:00:00Z", endsAt: "2026-11-01T07:00:00Z", breakMinutes: 0 },
        {
          ...context,
          workDate: "2026-11-01",
          companyDate: "2026-11-01",
          timezone: "America/New_York",
        },
      ).ok,
    ).toBe(true);
    expect(isOvertimePlanningDate("2026-09-12", "2026-10-13")).toBe(true);
    expect(isOvertimePlanningDate("2027-01-11", "2026-10-13")).toBe(true);
    expect(isOvertimePlanningDate("2026-09-11", "2026-10-13")).toBe(false);
    expect(isOvertimePlanningDate("2027-01-12", "2026-10-13")).toBe(false);
  });
});
