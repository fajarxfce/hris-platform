import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { RosterReset } from "../domain/entities/roster-reset";
import {
  access,
  calendarDtoFixture,
  company,
  employeeId,
  identifier,
  operation,
  signal,
} from "./calendar-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const range = { from: "2026-10-12", until: "2026-10-13" };
const reset = (): RosterReset => ({
  employeeId: employeeId.toUpperCase(),
  workDate: range.from,
  expectedVersion: 6,
  reason: " Restore regular calendar ",
});
const inactive = { workDate: range.from, version: 6, active: false, shift: null };

describe("retained roster reset boundaries", () => {
  it("retains an inactive head's version without changing the resolved calendar", async () => {
    const dto = { ...calendarDtoFixture(range), rosterOverrides: [{ ...inactive }] };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const result = await createWorkforceFeature({ request }).loadCalendarReview.execute(
      access,
      employeeId,
      range,
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        rosterOverrides: [inactive],
        supportsRosterReset: true,
        days: [{ origin: "PATTERN" }, { origin: "PATTERN" }],
      },
    });
    if (result.ok) {
      expect(Object.isFrozen(result.value.rosterOverrides)).toBe(true);
      expect(Object.isFrozen(result.value.rosterOverrides[0])).toBe(true);
      const original = dto.rosterOverrides[0];
      if (!original) throw new Error("Missing fixture override");
      original.version = 9;
      expect(result.value.rosterOverrides[0]?.version).toBe(6);
    }
  });

  it("rejects missing, duplicate, unsorted or inconsistent observed roster versions", async () => {
    const dto = calendarDtoFixture(range);
    const first = dto.calendar.days[0];
    if (!first?.shift) throw new Error("Missing fixture shift");
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const active = { ...inactive, active: true, shift: first.shift };
    const activeCalendar = {
      ...dto.calendar,
      days: [{ ...first, origin: "ROSTER", originVersion: 6 }, ...dto.calendar.days.slice(1)],
    };
    const invalid = [
      { ...dto, rosterOverrides: [{ ...inactive, workDate: "2026-02-30" }] },
      { ...dto, rosterOverrides: [{ ...inactive, workDate: "2026-10-14" }] },
      { ...dto, rosterOverrides: [inactive, inactive] },
      { ...dto, rosterOverrides: [{ ...inactive, workDate: range.until }, inactive] },
      { ...dto, rosterOverrides: [{ ...inactive, version: -1 }] },
      { ...dto, rosterOverrides: [{ ...inactive, version: 0.5 }] },
      { ...dto, rosterOverrides: [{ ...inactive, shift: first.shift }] },
      { ...dto, rosterOverrides: [active] },
      { ...dto, calendar: activeCalendar, rosterOverrides: [] },
      { ...dto, calendar: activeCalendar, rosterOverrides: [inactive] },
      { ...dto, calendar: activeCalendar, rosterOverrides: [{ ...active, version: 5 }] },
      { ...dto, calendar: activeCalendar, rosterOverrides: [{ ...active, shift: null }] },
      {
        ...dto,
        calendar: activeCalendar,
        rosterOverrides: [{ ...active, shift: { ...first.shift, revision: 1 } }],
      },
      {
        ...dto,
        calendar: activeCalendar,
        rosterOverrides: [
          {
            ...active,
            shift: {
              ...first.shift,
              details: { ...first.shift.details, name: "Different snapshot" },
            },
          },
        ],
      },
    ];
    for (const response of invalid) {
      request.mockResolvedValue(response);
      expect(
        await feature.loadCalendarReview.execute(access, employeeId, range, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValue({ ...dto, calendar: activeCalendar, rosterOverrides: [active] });
    expect((await feature.loadCalendarReview.execute(access, employeeId, range, signal())).ok).toBe(
      true,
    );
  });

  it("checks current management scope, dates and finite command versions before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    expect(
      await feature.resetRosterDay.execute(
        { ...access, permissions: ["workforce.read"] },
        operation,
        reset(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const input of [
      { ...reset(), employeeId: "../other" },
      { ...reset(), workDate: "2026-02-29" },
      { ...reset(), expectedVersion: null },
      ...[-1, 0.5, Number.MAX_SAFE_INTEGER, Number.NaN, Number.POSITIVE_INFINITY].map(
        (expectedVersion) => ({ ...reset(), expectedVersion }),
      ),
      { ...reset(), reason: " " },
      { ...reset(), reason: "x".repeat(1001) },
    ] as RosterReset[]) {
      expect(
        await feature.resetRosterDay.execute(access, operation, input, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_roster_change" } });
    }
    expect(request).not.toHaveBeenCalled();
  });

  it("sends one normalized scoped command and accepts only its exact receipt", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: employeeId, version: 7 });
    const feature = createWorkforceFeature({ request });
    const pending = signal();
    expect(await feature.resetRosterDay.execute(access, operation, reset(), pending)).toEqual({
      ok: true,
      value: { id: employeeId, version: 7 },
    });
    expect(request).toHaveBeenCalledExactlyOnceWith(
      {
        path: `/api/v1/companies/${company}/workforce/employees/${employeeId}/roster/${range.from}/reset`,
        method: "POST",
        operationId: operation,
        body: { expectedVersion: 6, reason: "Restore regular calendar" },
      },
      pending,
    );
    for (const receipt of [
      { id: identifier(), version: 7 },
      { id: employeeId, version: 6 },
      { id: employeeId, version: 8 },
    ]) {
      request.mockResolvedValue(receipt);
      expect(
        await feature.resetRosterDay.execute(access, operation, reset(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    for (const code of [
      "roster_override_required",
      "shift_timezone_gap",
      "stale_version",
      "work_period_locked",
    ]) {
      const failure = { code, fields: {}, parameters: {} };
      request.mockRejectedValue(new HttpResponseError(409, failure));
      expect(await feature.resetRosterDay.execute(access, operation, reset(), signal())).toEqual({
        ok: false,
        failure,
      });
    }
    expect(request).toHaveBeenCalledTimes(8);
  });

  it("propagates cancellation before acquisition and after a late command receipt", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() =>
      feature.resetRosterDay.execute(access, operation, reset(), cancelled.signal),
    ).toThrow();
    expect(request).not.toHaveBeenCalled();
    const pending = new AbortController();
    request.mockImplementation(async () => {
      pending.abort();
      return { id: employeeId, version: 7 };
    });
    await expect(
      feature.resetRosterDay.execute(access, operation, reset(), pending.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
