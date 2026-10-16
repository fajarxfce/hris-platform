import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { RosterDayChange } from "../domain/entities/roster-day-change";
import type { WeeklyScheduleChange } from "../domain/entities/weekly-schedule-change";
import {
  access,
  calendarDtoFixture,
  company,
  employeeDtoFixture,
  employeeId,
  identifier,
  operation,
  calendarRangeFixture as range,
  signal,
} from "./calendar-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const weekly = (): WeeklyScheduleChange => ({
  employeeId,
  effectiveFrom: range.from,
  days: { MONDAY: { id: identifier(), version: 0 } },
  expectedVersion: 4,
  reason: " New coverage ",
});
const roster = (): RosterDayChange => ({
  employeeId,
  workDate: range.from,
  shift: null,
  expectedVersion: null,
  reason: " Day off ",
});
describe("work-calendar data boundaries", () => {
  it("loads a coherent immutable review with its original snapshots and global schedule version", async () => {
    const dto = calendarDtoFixture();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const result = await createWorkforceFeature({ request }).loadCalendarReview.execute(
      access,
      employeeId.toUpperCase(),
      range,
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        scheduleVersion: 4,
        supportsRosterReset: false,
        employee: { id: employeeId, companyId: company },
        assignmentAtStart: { revision: 2, days: { MONDAY: { revision: 0 } } },
      },
    });
    if (result.ok) {
      expect(Object.isFrozen(result.value.days)).toBe(true);
      expect(Object.isFrozen(result.value.assignmentAtStart?.days.MONDAY?.details)).toBe(true);
      const shift = dto.assignmentAtStart?.days.MONDAY;
      if (shift) shift.details.name = "Mutated raw response";
      expect(result.value.assignmentAtStart?.days.MONDAY?.details.name).toBe("Shift 1");
    }
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/workforce/employees/${employeeId}/calendar/review?from=2026-10-12&until=2026-10-18`,
    );
  });
  it("rejects invalid dates, permissions, references and versions before any transport I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    for (const permissions of [
      [],
      ["company.read"],
      ["people.read"],
      ["attendance.self.record"],
      ["workforce.team.read"],
    ])
      expect(
        await feature.loadCalendarReview.execute(
          { ...access, permissions },
          employeeId,
          range,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const input of [
      { from: "2026-02-29", until: "2026-03-01" },
      { from: "2026-10-12", until: "2026-10-11" },
      { from: "2026-01-01", until: "2026-03-04" },
    ])
      expect(
        (await feature.loadCalendarReview.execute(access, employeeId, input, signal())).ok,
      ).toBe(false);
    expect((await feature.loadCalendarReview.execute(access, "../other", range, signal())).ok).toBe(
      false,
    );
    for (const change of [
      { ...weekly(), effectiveFrom: "0000-01-01" },
      { ...weekly(), employeeId: "../other" },
      { ...weekly(), expectedVersion: -1 },
      { ...weekly(), expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...weekly(), reason: " " },
      { ...weekly(), days: { MONDAY: { id: identifier(), version: -1 } } },
      { ...weekly(), days: { INVALID: { id: identifier(), version: 0 } } },
    ] as WeeklyScheduleChange[])
      expect(
        (await feature.assignWeeklySchedule.execute(access, operation, change, signal())).ok,
      ).toBe(false);
    expect(
      (
        await feature.assignWeeklySchedule.execute(
          { ...access, permissions: ["workforce.read"] },
          operation,
          weekly(),
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await feature.setRosterDay.execute(
          access,
          operation,
          { ...roster(), shift: { id: "../other", version: 0 } },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await feature.setRosterDay.execute(
          access,
          operation,
          { ...roster(), workDate: "2026-02-30" },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(request).not.toHaveBeenCalled();
  });
  it("rejects foreign, mixed-version, incomplete and inconsistent calendar responses", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const valid = calendarDtoFixture();
    const first = valid.calendar.days[0];
    if (!first) throw new Error("Fixture day missing");
    const invalid = [
      { ...valid, employee: { ...valid.employee, id: identifier(502) } },
      { ...valid, from: "2026-10-13" },
      { ...valid, assignmentAtStart: null },
      { ...valid, assignmentAtStart: { ...valid.assignmentAtStart, revision: 9 } },
      { ...valid, calendar: { ...valid.calendar, days: valid.calendar.days.slice(1) } },
      {
        ...valid,
        calendar: { ...valid.calendar, days: [first, ...valid.calendar.days.slice(0, 6)] },
      },
      ...[
        { ...first, plannedMinutes: 451 },
        { ...first, endsAt: first.startsAt },
        { ...first, kind: "UNASSIGNED" },
        { ...first, origin: "HOLIDAY" },
        { ...first, originVersion: 3 },
      ].map((day) => ({
        ...valid,
        calendar: { ...valid.calendar, days: [day, ...valid.calendar.days.slice(1)] },
      })),
    ];
    for (const dto of invalid) {
      request.mockResolvedValue(dto);
      expect(
        await feature.loadCalendarReview.execute(access, employeeId, range, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("distinguishes explicit roster days off, holidays and dates before a future schedule", async () => {
    const valid = calendarDtoFixture({ from: range.from, until: range.from });
    const off = {
      ...valid.calendar.days[0],
      kind: "OFF",
      shift: null,
      startsAt: null,
      endsAt: null,
      plannedMinutes: 0,
      origin: "ROSTER",
      originVersion: 7,
      holidayId: null,
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ ...valid, calendar: { ...valid.calendar, days: [off] } });
    const feature = createWorkforceFeature({ request });
    expect(
      await feature.loadCalendarReview.execute(
        access,
        employeeId,
        { from: range.from, until: range.from },
        signal(),
      ),
    ).toMatchObject({
      ok: true,
      value: { days: [{ kind: "OFF", origin: "ROSTER", originVersion: 7 }] },
    });
    request.mockResolvedValue({
      ...valid,
      calendar: {
        ...valid.calendar,
        days: [{ ...off, origin: "HOLIDAY", holidayId: identifier(20) }],
      },
    });
    expect(
      (
        await feature.loadCalendarReview.execute(
          access,
          employeeId,
          { from: range.from, until: range.from },
          signal(),
        )
      ).ok,
    ).toBe(true);
    request.mockResolvedValue({
      ...valid,
      assignmentAtStart: null,
      calendar: {
        ...valid.calendar,
        days: [{ ...off, kind: "UNASSIGNED", origin: null, originVersion: null }],
      },
    });
    expect(
      await feature.loadCalendarReview.execute(
        access,
        employeeId,
        { from: range.from, until: range.from },
        signal(),
      ),
    ).toMatchObject({
      ok: true,
      value: { scheduleVersion: 4, assignmentAtStart: null, days: [{ kind: "UNASSIGNED" }] },
    });
  });
  it("maps normalized commands and validates the exact employee/version receipt without retry", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: employeeId.toUpperCase(), version: 5 });
    const feature = createWorkforceFeature({ request });
    expect(
      (await feature.assignWeeklySchedule.execute(access, operation, weekly(), signal())).ok,
    ).toBe(true);
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/workforce/employees/${employeeId}/schedule`,
      method: "PUT",
      operationId: operation,
      body: {
        effectiveFrom: range.from,
        days: { MONDAY: { id: identifier(), version: 0 } },
        expectedVersion: 4,
        reason: "New coverage",
      },
    });
    request.mockResolvedValue({ id: employeeId, version: 0 });
    expect((await feature.setRosterDay.execute(access, operation, roster(), signal())).ok).toBe(
      true,
    );
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/workforce/employees/${employeeId}/roster/${range.from}`,
      method: "PUT",
      operationId: operation,
      body: { shift: null, expectedVersion: null, reason: "Day off" },
    });
    request.mockResolvedValue({ id: identifier(), version: 0 });
    expect(await feature.setRosterDay.execute(access, operation, roster(), signal())).toMatchObject(
      { ok: false, failure: { code: "invalid_response" } },
    );
    request.mockResolvedValue({ id: employeeId, version: 2 });
    expect(await feature.setRosterDay.execute(access, operation, roster(), signal())).toMatchObject(
      { ok: false, failure: { code: "invalid_response" } },
    );
    const failure = { code: "work_period_locked", fields: {}, parameters: {} };
    request.mockRejectedValue(new HttpResponseError(409, failure));
    expect(
      await feature.assignWeeklySchedule.execute(access, operation, weekly(), signal()),
    ).toEqual({ ok: false, failure });
    expect(request).toHaveBeenCalledTimes(5);
  });
  it("bounds employee pages, rejects non-progressing cursors and keeps profile data out of the entity", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      items: [{ ...employeeDtoFixture(), email: "not-part-of-the-contract@example.invalid" }],
      nextCursor: null,
    });
    const feature = createWorkforceFeature({ request });
    const search = { asOf: range.from, query: " Employee ", after: null };
    const accepted = await feature.loadWorkforceEmployees.execute(access, search, signal());
    expect(accepted.ok).toBe(true);
    if (accepted.ok) {
      expect(accepted.value.items[0]).not.toHaveProperty("email");
      expect(Object.isFrozen(accepted.value.items)).toBe(true);
    }
    expect(request.mock.lastCall?.[0].path).toContain("asOf=2026-10-12&query=Employee&limit=50");
    for (const dto of [
      { items: [employeeDtoFixture(), employeeDtoFixture()], nextCursor: null },
      { items: [employeeDtoFixture()], nextCursor: "EMP-001" },
      { items: Array.from({ length: 51 }, employeeDtoFixture), nextCursor: null },
    ]) {
      request.mockResolvedValue(dto);
      expect(await feature.loadWorkforceEmployees.execute(access, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    const before = request.mock.calls.length;
    expect(
      (
        await feature.loadWorkforceEmployees.execute(
          access,
          { ...search, query: "x".repeat(121) },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (await feature.loadWorkforceEmployees.execute(access, { ...search, after: "" }, signal())).ok,
    ).toBe(false);
    expect(request).toHaveBeenCalledTimes(before);
  });
  it("propagates cancellation before a call and after a late response", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() =>
      feature.loadCalendarReview.execute(access, employeeId, range, cancelled.signal),
    ).toThrow();
    expect(request).not.toHaveBeenCalled();
    const pending = new AbortController();
    request.mockImplementation(async () => {
      pending.abort();
      return calendarDtoFixture();
    });
    await expect(
      feature.loadCalendarReview.execute(access, employeeId, range, pending.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
