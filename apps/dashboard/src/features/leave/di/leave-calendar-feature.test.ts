import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { LeaveCalendarDto, LeaveCalendarEntriesDto } from "../data/models/leave-calendar-dto";
import type { LeaveCalendarScope } from "../domain/entities/leave-calendar";
import {
  calendarAccess,
  calendarCompany,
  calendarDeferred,
  calendarEntriesWire,
  calendarRequestId,
  calendarWire,
} from "./leave-calendar-fixture";
import { createLeaveFeature } from "./leave-feature";

const signal = () => new AbortController().signal;
const setup = (wire: unknown = calendarWire) => {
  const request = vi
    .fn<(input: HttpRequest, signal: AbortSignal) => Promise<unknown>>()
    .mockResolvedValue(wire);
  return { request, feature: createLeaveFeature({ request }, { downloadBinary: vi.fn() }) };
};
describe("leave calendar boundary", () => {
  it("uses company-local default month and retains detached immutable counts", async () => {
    const wire = structuredClone(calendarWire),
      { feature, request } = setup(wire);
    const result = await feature.loadCalendar.execute(calendarAccess, "COMPANY", null, signal());
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${calendarCompany}/leave/calendar?scope=COMPANY`,
    );
    const day = wire.days[0];
    if (!day) throw Error("Missing fixture day");
    day.employeeCount = 999;
    expect(result.value.days[0]?.employeeCount).toBe(1);
    expect(Object.isFrozen(result.value.days)).toBe(true);
    expect(Object.isFrozen(result.value.days[0])).toBe(true);
  });
  it("allows team-only scope and does not acquire company data", async () => {
    const { feature, request } = setup({ ...calendarWire, scope: "TEAM" });
    const access = { ...calendarAccess, permissions: ["leave.team.read"] };
    expect((await feature.loadCalendar.execute(access, "TEAM", "2026-10", signal())).ok).toBe(true);
    request.mockClear();
    expect(
      await feature.loadCalendar.execute(access, "COMPANY", "2026-10", signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(request).not.toHaveBeenCalled();
  });
  it.each(
    [[], ["leave.self.manage"], ["leave.approve", "leave.team.approve"], ["people.read"]].map(
      (permissions) => ({ permissions }),
    ),
  )("requires independent calendar read scope %j", async ({ permissions }) => {
    const { feature, request } = setup();
    const access = { ...calendarAccess, permissions };
    expect((await feature.loadCalendar.execute(access, "TEAM", null, signal())).ok).toBe(false);
    expect(
      (await feature.loadCalendarEntries.execute(access, "TEAM", "2026-10-15", null, signal())).ok,
    ).toBe(false);
    expect(request).not.toHaveBeenCalled();
  });
  it.each(["1899-12", "2201-01", "2026-00", "2026-13", "2026-1", "2026-10-01"])(
    "rejects invalid month %s before I/O",
    async (month) => {
      const { feature, request } = setup();
      expect(
        (await feature.loadCalendar.execute(calendarAccess, "COMPANY", month, signal())).ok,
      ).toBe(false);
      expect(request).not.toHaveBeenCalled();
    },
  );
  it.each(["2026-02-29", "1899-12-31", "2201-01-01", "2026-10-5"])(
    "rejects invalid day %s before I/O",
    async (date) => {
      const { feature, request } = setup();
      expect(
        (await feature.loadCalendarEntries.execute(calendarAccess, "COMPANY", date, null, signal()))
          .ok,
      ).toBe(false);
      expect(request).not.toHaveBeenCalled();
    },
  );
  it("rejects unrecognized scope and malformed cursor without requests", async () => {
    const { feature, request } = setup();
    expect(
      (
        await feature.loadCalendar.execute(
          calendarAccess,
          "ALL" as LeaveCalendarScope,
          null,
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await feature.loadCalendarEntries.execute(
          calendarAccess,
          "COMPANY",
          "2026-10-15",
          "bad",
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(request).not.toHaveBeenCalled();
  });
  it.each([
    (raw: LeaveCalendarDto) => {
      raw.scope = "TEAM";
    },
    (raw: LeaveCalendarDto) => {
      raw.month = "2026-11";
    },
    (raw: LeaveCalendarDto) => {
      raw.companyDate = "2026-02-30";
    },
    (raw: LeaveCalendarDto) => {
      raw.timezone = "invalid/timezone";
    },
    (raw: LeaveCalendarDto) => {
      const day = raw.days[0];
      if (day) raw.days.push({ ...day });
    },
    (raw: LeaveCalendarDto) => {
      raw.days.reverse();
    },
    (raw: LeaveCalendarDto) => {
      const day = raw.days[0];
      if (day) day.date = "2026-11-01";
    },
    (raw: LeaveCalendarDto) => {
      const day = raw.days[0];
      if (day) day.requestCount = 1;
    },
    (raw: LeaveCalendarDto) => {
      const day = raw.days[0];
      if (day) day.employeeCount = 0;
    },
    (raw: LeaveCalendarDto) => {
      const day = raw.days[0];
      if (day) day.pendingCount = 2;
    },
  ])("rejects incoherent or unbounded month response %$", async (change) => {
    const raw = structuredClone(calendarWire);
    change(raw);
    const { feature } = setup(raw);
    expect(
      await feature.loadCalendar.execute(calendarAccess, "COMPANY", "2026-10", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("fetches one bounded date page and exposes only normalized immutable entry fields", async () => {
    const wire = structuredClone(calendarEntriesWire),
      { feature, request } = setup(wire);
    const result = await feature.loadCalendarEntries.execute(
      calendarAccess,
      "COMPANY",
      wire.date,
      null,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(request.mock.calls[0]?.[0].path).toContain(
      "calendar/entries?scope=COMPANY&date=2026-10-15&limit=20",
    );
    expect(result.value.entries.items[0]).toMatchObject({
      portion: "FIRST_HALF",
      status: "CANCELLATION_PENDING",
    });
    expect(Object.isFrozen(result.value.entries.items)).toBe(true);
  });
  it.each([
    (raw: LeaveCalendarEntriesDto) => {
      raw.date = "2026-10-16";
    },
    (raw: LeaveCalendarEntriesDto) => {
      raw.scope = "TEAM";
    },
    (raw: LeaveCalendarEntriesDto) => {
      const row = raw.entries.items[0];
      if (row) raw.entries.items.push({ ...row });
    },
    (raw: LeaveCalendarEntriesDto) => {
      raw.entries.nextCursor = calendarRequestId(9);
    },
    (raw: LeaveCalendarEntriesDto) => {
      const row = raw.entries.items[0];
      if (row) raw.entries.items = Array.from({ length: 21 }, () => ({ ...row }));
    },
    (raw: LeaveCalendarEntriesDto) => {
      const row = raw.entries.items[0];
      if (row) row.status = "CANCELLED" as never;
    },
    (raw: LeaveCalendarEntriesDto) => {
      const row = raw.entries.items[0];
      if (row) row.portion = "INVALID" as never;
    },
  ])("rejects inconsistent day response %$", async (change) => {
    const raw = structuredClone(calendarEntriesWire);
    change(raw);
    const { feature } = setup(raw);
    expect(
      await feature.loadCalendarEntries.execute(
        calendarAccess,
        "COMPANY",
        "2026-10-15",
        null,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("cannot replay a cursor page that fails to advance", async () => {
    const { feature } = setup(calendarEntriesWire);
    expect(
      (
        await feature.loadCalendarEntries.execute(
          calendarAccess,
          "COMPANY",
          "2026-10-15",
          calendarRequestId(1),
          signal(),
        )
      ).ok,
    ).toBe(false);
  });
  it("preserves cancellation even if a client completes late", async () => {
    const pending = calendarDeferred<unknown>(),
      { feature, request } = setup();
    request.mockReturnValue(pending.promise);
    const owner = new AbortController();
    const result = feature.loadCalendar.execute(calendarAccess, "COMPANY", null, owner.signal);
    owner.abort();
    pending.resolve(calendarWire);
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
  });
  it("keeps safe error codes and discards technical response text", async () => {
    const { feature, request } = setup();
    request.mockRejectedValue(
      new HttpResponseError(403, {
        code: "mfa_required",
        detail: "PRIVATE CALENDAR DIAGNOSTIC",
        fields: {},
        parameters: {},
      }),
    );
    const result = await feature.loadCalendar.execute(calendarAccess, "COMPANY", null, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE CALENDAR DIAGNOSTIC");
  });
});
