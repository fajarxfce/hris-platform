import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import type { OperationId } from "../../../core/domain/identifiers";
import {
  attendanceAccess,
  attendanceDate,
  attendanceDtoFixture,
  employeeId,
  eventId,
  identifier,
} from "./attendance-fixture";
import { calendarDtoFixture } from "./calendar-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const range = { from: attendanceDate, until: attendanceDate };
const operation = identifier(99) as OperationId;
const signal = () => new AbortController().signal;
function fixture() {
  const request = vi.fn<HttpClient["request"]>().mockImplementation(async (request) => {
    if (request.path.includes("/calendar/review")) return calendarDtoFixture(range);
    return attendanceDtoFixture(range);
  });
  return { request, feature: createWorkforceFeature({ request }) };
}
describe("attendance feature boundary", () => {
  it("selects a bounded employee reference and maps immutable captured evidence", async () => {
    const f = fixture(),
      result = await f.feature.loadEmployeeAttendance.execute(
        attendanceAccess,
        employeeId,
        range,
        signal(),
      );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.days[0]?.entries[0]?.schedule.workDate).toBe(attendanceDate);
    expect(result.value.employee.id).toBe(employeeId);
    expect(Object.isFrozen(result.value.days[0]?.entries[0]?.issues)).toBe(true);
    expect(f.request).toHaveBeenCalledTimes(2);
  });
  it("rejects invalid ranges and unauthorized commands before transport", async () => {
    const f = fixture();
    for (const dates of [
      { from: "2026-02-30", until: "2026-03-01" },
      { from: attendanceDate, until: "2026-11-12" },
    ])
      expect(
        (
          await f.feature.loadEmployeeAttendance.execute(
            attendanceAccess,
            employeeId,
            dates,
            signal(),
          )
        ).ok,
      ).toBe(false);
    expect(
      (
        await f.feature.reviewAttendance.execute(
          { ...attendanceAccess, permissions: ["workforce.read"] },
          operation,
          { entryId: eventId, version: 0, decision: "ACCEPT", reason: "Independent verification" },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each(["date", "duplicate", "pending", "review", "instant", "correction"] as const)(
    "rejects incoherent %s evidence inside the HTTP boundary",
    async (fault) => {
      const f = fixture(),
        data = attendanceDtoFixture(range),
        day = data[0],
        entry = day?.entries[0];
      if (!day || !entry) throw new Error("Missing fixture");
      if (fault === "date") day.workDate = "2026-10-13";
      if (fault === "duplicate") {
        day.entries.push({ ...entry });
        day.pendingCount++;
      }
      if (fault === "pending") day.pendingCount = 0;
      if (fault === "review") entry.status = "REJECTED";
      if (fault === "instant") entry.capturedAt = "2026-02-30T03:00:00Z";
      if (fault === "correction")
        day.correction = {
          id: identifier(501),
          actorId: identifier(1),
          clockIn: "2026-10-12T03:00:00Z",
          clockOut: null,
          breakMinutes: 0,
          reason: "Correction",
          recordedAt: "2026-10-12T04:00:00Z",
          version: 0,
        };
      f.request.mockImplementation(async (request) =>
        request.path.includes("/calendar/review") ? calendarDtoFixture(range) : data,
      );
      const result = await f.feature.loadEmployeeAttendance.execute(
        attendanceAccess,
        employeeId,
        range,
        signal(),
      );
      expect(result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    },
  );
  it("pins the observed event version and normalizes the review body", async () => {
    const f = fixture();
    f.request.mockResolvedValue({ id: eventId, version: 1 });
    const result = await f.feature.reviewAttendance.execute(
      attendanceAccess,
      operation,
      { entryId: eventId, version: 0, decision: "REJECT", reason: " Wrong event " },
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { id: eventId, version: 1 } });
    expect(f.request.mock.calls[0]?.[0]).toMatchObject({
      method: "POST",
      operationId: operation,
      body: { version: 0, decision: "REJECT", reason: "Wrong event" },
    });
    f.request.mockResolvedValue({ id: identifier(92), version: 1 });
    expect(
      (
        await f.feature.reviewAttendance.execute(
          attendanceAccess,
          operation,
          { entryId: eventId, version: 0, decision: "ACCEPT", reason: "Verified" },
          signal(),
        )
      ).ok,
    ).toBe(false);
  });
  it("keeps explicit absence and later correction versions distinct", async () => {
    const f = fixture();
    const correction = {
      employeeId,
      workDate: attendanceDate,
      clockIn: null,
      clockOut: null,
      breakMinutes: 0,
      expectedVersion: null,
      reason: " Verified absence ",
    };
    f.request.mockResolvedValue({ id: identifier(903), version: 0 });
    expect(
      (await f.feature.correctAttendance.execute(attendanceAccess, operation, correction, signal()))
        .ok,
    ).toBe(true);
    expect(f.request.mock.calls[0]?.[0].body).toEqual({
      workDate: attendanceDate,
      clockIn: null,
      clockOut: null,
      breakMinutes: 0,
      expectedVersion: null,
      reason: "Verified absence",
    });
    expect(
      (
        await f.feature.correctAttendance.execute(
          attendanceAccess,
          operation,
          { ...correction, breakMinutes: 1 },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(f.request).toHaveBeenCalledTimes(1);
  });
  it("propagates cancellation between metadata and evidence acquisition", async () => {
    const f = fixture(),
      pending = new AbortController();
    f.request.mockImplementation(async () => {
      pending.abort();
      return calendarDtoFixture(range);
    });
    await expect(
      f.feature.loadEmployeeAttendance.execute(attendanceAccess, employeeId, range, pending.signal),
    ).rejects.toThrow();
    expect(f.request).toHaveBeenCalledTimes(1);
  });
});
