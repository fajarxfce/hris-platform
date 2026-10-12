import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import {
  attendanceCorrectionDtoFixture,
  attendanceCorrectionPageDtoFixture,
} from "./attendance-correction-fixture";
import {
  attendanceAccess,
  attendanceDate,
  company,
  employeeId,
  identifier,
} from "./attendance-fixture";
import { calendarDtoFixture } from "./calendar-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const range = { from: attendanceDate, until: attendanceDate };
function fixture() {
  const history = attendanceCorrectionPageDtoFixture();
  const request = vi.fn<HttpClient["request"]>().mockImplementation(async (request) => {
    if (request.path.includes("/calendar/review")) return calendarDtoFixture(range);
    return history;
  });
  const feature = createWorkforceFeature({ request });
  return {
    request,
    history,
    feature,
    load: (after: string | null = null, signal = new AbortController().signal) =>
      feature.loadAttendanceCorrectionHistory.execute(
        attendanceAccess,
        employeeId,
        attendanceDate,
        after,
        signal,
      ),
  };
}

describe("attendance correction history boundary", () => {
  it("maps a scoped, immutable page and advances only its exclusive revision cursor", async () => {
    const f = fixture();
    const first = await f.load();
    expect(first.ok).toBe(true);
    if (!first.ok) return;
    expect(first.value).toMatchObject({
      companyId: company,
      employeeId,
      workDate: attendanceDate,
      employee: { id: employeeId, legalName: "Calendar Employee" },
      nextCursor: "3",
    });
    expect(first.value.items).toHaveLength(50);
    expect(first.value.items[0]?.version).toBe(52);
    expect(first.value.items.at(-1)?.version).toBe(3);
    expect(Object.isFrozen(first.value)).toBe(true);
    expect(Object.isFrozen(first.value.items)).toBe(true);
    expect(Object.isFrozen(first.value.items[0])).toBe(true);
    expect(Object.isFrozen(first.value.employee)).toBe(true);
    const original = f.history.items[0],
      request = f.request.mock.calls[1]?.[0];
    if (!original || !request) throw new Error("Missing correction fixture or request");
    original.reason = "Changed transport value";
    expect(first.value.items[0]?.reason).toBe("Attendance correction 52");
    const url = new URL(request.path, "https://example.invalid");
    expect(url.pathname).toBe(
      `/api/v1/companies/${company}/workforce/employees/${employeeId}/attendance/corrections`,
    );
    expect(Object.fromEntries(url.searchParams)).toEqual({ workDate: attendanceDate, limit: "50" });
    f.request.mockImplementation(async (request) =>
      request.path.includes("/calendar/review")
        ? calendarDtoFixture(range)
        : attendanceCorrectionPageDtoFixture("3"),
    );
    const older = await f.load(first.value.nextCursor);
    expect(older).toMatchObject({
      ok: true,
      value: { items: [{ version: 2 }, { version: 1 }, { version: 0 }], nextCursor: null },
    });
    expect(f.request.mock.calls[3]?.[0].path).toContain("after=3");
  });

  it.each([null, "0"])(
    "accepts an empty page at cursor %s without inventing a correction",
    async (after) => {
      const f = fixture();
      f.history.items = [];
      f.history.nextCursor = null;
      expect(await f.load(after)).toMatchObject({
        ok: true,
        value: { items: [], nextCursor: null },
      });
    },
  );

  it.each(["", "-1", "01", "1e2", "1.0", "9007199254740992", "9".repeat(100)])(
    "rejects invalid revision cursor %s before acquiring either source",
    async (after) => {
      const f = fixture();
      expect(await f.load(after)).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
      expect(f.request).not.toHaveBeenCalled();
    },
  );

  it("requires administrative read access and valid employee/date inputs", async () => {
    const f = fixture();
    for (const [access, employee, workDate, code] of [
      [
        { ...attendanceAccess, permissions: ["attendance.correct"] },
        employeeId,
        attendanceDate,
        "access_denied",
      ],
      [attendanceAccess, "../other", attendanceDate, "employee_not_found"],
      [attendanceAccess, employeeId, "2026-02-30", "invalid_attendance_range"],
    ] as const) {
      expect(
        await f.feature.loadAttendanceCorrectionHistory.execute(
          access,
          employee,
          workDate,
          null,
          new AbortController().signal,
        ),
      ).toMatchObject({ ok: false, failure: { code } });
    }
    expect(f.request).not.toHaveBeenCalled();
  });

  it.each([
    "duplicate identity",
    "duplicate version",
    "wrong ordering",
    "unsafe version",
    "oversized page",
    "short continued page",
    "nonprogressing cursor",
    "incomplete times",
    "invalid timestamp",
    "blank reason",
  ] as const)("rejects %s within the data failure boundary", async (fault) => {
    const f = fixture();
    const first = f.history.items[0],
      second = f.history.items[1];
    if (!first || !second) throw new Error("Missing correction fixtures");
    if (fault === "duplicate identity") second.id = first.id.toUpperCase();
    if (fault === "duplicate version") second.version = first.version;
    if (fault === "wrong ordering") f.history.items.reverse();
    if (fault === "unsafe version") first.version = Number.MAX_SAFE_INTEGER + 1;
    if (fault === "oversized page") f.history.items.push(attendanceCorrectionDtoFixture());
    if (fault === "short continued page") f.history.items.pop();
    if (fault === "nonprogressing cursor") f.history.nextCursor = "52";
    if (fault === "incomplete times") first.clockOut = null;
    if (fault === "invalid timestamp") first.recordedAt = "2026-02-30T03:00:00Z";
    if (fault === "blank reason") first.reason = " ";
    expect(await f.load()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("rejects a version at or above the requested exclusive cursor", async () => {
    const f = fixture();
    expect(await f.load("52")).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it.each(["reference", "history"] as const)(
    "preserves access/MFA failures from %s without leaking technical text",
    async (source) => {
      const f = fixture();
      f.request.mockImplementation(async (request) => {
        if (source === "reference" || request.path.includes("/corrections"))
          throw new HttpResponseError(403, { code: "mfa_required", detail: "Private response" });
        return calendarDtoFixture(range);
      });
      expect(await f.load()).toEqual({
        ok: false,
        failure: { code: "mfa_required", fields: {}, parameters: {} },
      });
      expect(f.request).toHaveBeenCalledTimes(source === "reference" ? 1 : 2);
    },
  );

  it.each(["before", "reference", "history"] as const)(
    "propagates cancellation %s and discards late data",
    async (step) => {
      const f = fixture(),
        owner = new AbortController();
      if (step === "before") owner.abort();
      f.request.mockImplementation(async (request) => {
        const reference = request.path.includes("/calendar/review");
        if ((step === "reference") === reference) owner.abort();
        return reference ? calendarDtoFixture(range) : f.history;
      });
      await expect(f.load(null, owner.signal)).rejects.toThrow();
      expect(f.request).toHaveBeenCalledTimes(step === "before" ? 0 : step === "reference" ? 1 : 2);
    },
  );

  it("does not acquire history when the employee reference belongs to another resource", async () => {
    const f = fixture(),
      reference = calendarDtoFixture(range);
    reference.employee.id = identifier(999);
    f.request.mockResolvedValue(reference);
    expect(await f.load()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
});
