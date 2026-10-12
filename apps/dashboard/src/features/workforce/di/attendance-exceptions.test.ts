import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AttendanceExceptionSearchInput } from "../domain/entities/attendance-exception-search";
import {
  attendanceExceptionCursorFixture,
  attendanceExceptionDtoFixture,
  attendanceExceptionPageDtoFixture,
  exceptionSearch,
} from "./attendance-exception-fixture";
import { attendanceAccess, company, identifier } from "./attendance-fixture";
import { createWorkforceFeature } from "./workforce-feature";

function fixture() {
  const page = attendanceExceptionPageDtoFixture();
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(page);
  const feature = createWorkforceFeature({ request });
  return {
    request,
    page,
    feature,
    load: (
      search: AttendanceExceptionSearchInput = exceptionSearch,
      signal = new AbortController().signal,
    ) => feature.loadAttendanceExceptions.execute(attendanceAccess, search, signal),
  };
}

describe("company attendance exception boundary", () => {
  it("returns one immutable company/filter page and follows its exclusive cursor", async () => {
    const f = fixture();
    const first = await f.load();
    expect(first.ok).toBe(true);
    if (!first.ok) return;
    expect(first.value.companyId).toBe(company);
    expect(first.value.search).toEqual(exceptionSearch);
    expect(first.value.items).toHaveLength(50);
    expect(first.value.items[0]).toMatchObject({ employeeName: "North Employee", pendingCount: 1 });
    expect(Object.isFrozen(first.value)).toBe(true);
    expect(Object.isFrozen(first.value.search)).toBe(true);
    expect(Object.isFrozen(first.value.items)).toBe(true);
    expect(Object.isFrozen(first.value.items[0])).toBe(true);
    const original = f.page.items[0];
    if (!original) throw new Error("Missing exception fixture");
    original.employeeName = "Changed transport value";
    expect(first.value.items[0]?.employeeName).toBe("North Employee");
    const url = new URL(f.request.mock.calls[0]?.[0].path ?? "", "https://example.invalid");
    expect(url.pathname).toBe(`/api/v1/companies/${company}/workforce/attendance/exceptions`);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      from: "2026-10-01",
      until: "2026-10-31",
      limit: "50",
    });
    const search = { ...exceptionSearch, after: first.value.nextCursor };
    f.request.mockResolvedValue(attendanceExceptionPageDtoFixture(search));
    const next = await f.load(search);
    expect(next).toMatchObject({ ok: true, value: { nextCursor: null } });
    if (!next.ok) return;
    expect(next.value.items).toHaveLength(3);
    expect(
      new URL(f.request.mock.calls[1]?.[0].path ?? "", "https://example.invalid").searchParams.get(
        "after",
      ),
    ).toBe(first.value.nextCursor);
  });

  it.each([null, "PENDING", "INCOMPLETE"] as const)(
    "retains an empty %s page without inferring absence",
    async (kind) => {
      const f = fixture();
      f.page.items = [];
      f.page.nextCursor = null;
      expect(await f.load({ ...exceptionSearch, kind })).toMatchObject({
        ok: true,
        value: { items: [], nextCursor: null, search: { kind } },
      });
      expect(f.request).toHaveBeenCalledTimes(1);
      expect(
        new URL(
          f.request.mock.calls[0]?.[0].path ?? "",
          "https://example.invalid",
        ).searchParams.get("kind"),
      ).toBe(kind);
    },
  );

  it("validates a filter against the returned facts", async () => {
    const f = fixture(),
      search = { ...exceptionSearch, kind: "INCOMPLETE" as const };
    f.request.mockResolvedValue(attendanceExceptionPageDtoFixture(search));
    const result = await f.load(search);
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.value.items.every((item) => item.incomplete)).toBe(true);
    f.request.mockResolvedValue(attendanceExceptionPageDtoFixture());
    expect(await f.load(search)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it.each([
    [{ from: "2026-09-30" }, "invalid_attendance_range"],
    [{ until: "2026-02-30" }, "invalid_attendance_range"],
    [{ kind: "ABSENT" }, "invalid_attendance_filter"],
    [{ after: "" }, "invalid_page"],
    [{ after: "x".repeat(161) }, "invalid_page"],
  ] as const)("rejects invalid search input %j before I/O", async (change, code) => {
    const f = fixture();
    expect(await f.load({ ...exceptionSearch, ...change })).toMatchObject({
      ok: false,
      failure: { code },
    });
    expect(f.request).not.toHaveBeenCalled();
  });

  it("does not turn self or team verification into company-wide directory access", async () => {
    const f = fixture();
    expect(
      await f.feature.loadAttendanceExceptions.execute(
        { ...attendanceAccess, permissions: ["attendance.self.record", "attendance.team.verify"] },
        exceptionSearch,
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).not.toHaveBeenCalled();
  });

  it.each([
    "duplicate day",
    "wrong ordering",
    "outside range",
    "invalid date",
    "invalid instant",
    "blank employee",
    "oversized count",
    "unsafe revision",
    "resolved row",
    "corrected incomplete",
    "short continued page",
    "foreign cursor",
    "nonprogressing cursor",
    "oversized page",
  ] as const)("rejects %s within the shared data failure boundary", async (fault) => {
    const f = fixture(),
      first = f.page.items[0],
      second = f.page.items[1],
      last = f.page.items.at(-1);
    if (!first || !second || !last) throw new Error("Missing exception fixtures");
    if (fault === "duplicate day") second.employeeId = first.employeeId.toUpperCase();
    if (fault === "wrong ordering") f.page.items.reverse();
    if (fault === "outside range") first.workDate = "2026-11-01";
    if (fault === "invalid date") first.workDate = "2026-02-30";
    if (fault === "invalid instant") first.lastReceivedAt = "2026-02-30T03:00:00Z";
    if (fault === "blank employee") first.employeeName = " ";
    if (fault === "oversized count") first.pendingCount = 33;
    if (fault === "unsafe revision") first.correctionVersion = Number.MAX_SAFE_INTEGER + 1;
    if (fault === "resolved row") first.pendingCount = 0;
    if (fault === "corrected incomplete") second.correctionVersion = 0;
    if (fault === "short continued page") f.page.items.pop();
    if (fault === "foreign cursor")
      f.page.nextCursor = attendanceExceptionCursorFixture(exceptionSearch, last, identifier(99));
    if (fault === "nonprogressing cursor")
      f.page.nextCursor = attendanceExceptionCursorFixture(exceptionSearch, first);
    if (fault === "oversized page") f.page.items.push(attendanceExceptionDtoFixture(50));
    expect(await f.load()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("rejects an old row at the exclusive continuation boundary", async () => {
    const f = fixture();
    expect(await f.load({ ...exceptionSearch, after: f.page.nextCursor })).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it.each(["mfa_required", "company_access_denied", "invalid_page"])(
    "preserves %s without private transport text",
    async (code) => {
      const f = fixture();
      f.request.mockRejectedValue(
        new HttpResponseError(403, { code, detail: "Private transport diagnostic" }),
      );
      expect(await f.load()).toEqual({ ok: false, failure: { code, fields: {}, parameters: {} } });
    },
  );

  it.each(["before", "during"] as const)(
    "propagates cancellation %s acquisition and discards late data",
    async (step) => {
      const f = fixture(),
        owner = new AbortController();
      if (step === "before") owner.abort();
      f.request.mockImplementation(async () => {
        owner.abort();
        return f.page;
      });
      await expect(f.load(exceptionSearch, owner.signal)).rejects.toMatchObject({
        name: "AbortError",
      });
      expect(f.request).toHaveBeenCalledTimes(step === "before" ? 0 : 1);
    },
  );
});
