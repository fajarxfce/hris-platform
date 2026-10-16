import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import type { OperationId } from "../../../core/domain/identifiers";
import {
  adjustmentAccess,
  adjustmentChange,
  adjustmentDate,
  adjustmentDetailsDto,
  adjustmentId,
  adjustmentOperation,
  adjustmentReviewDto,
  company,
  employeeId,
  identifier,
  periodMonth,
} from "./attendance-adjustment-fixture";
import { workPeriodSnapshotDtoFixture } from "./work-period-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const signal = () => new AbortController().signal;
describe("closed attendance boundaries", () => {
  it("acquires one scoped review and detaches the entire evidence graph", async () => {
    const raw = adjustmentReviewDto();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const result = await createWorkforceFeature({ request }).loadAttendanceAdjustmentReview.execute(
      adjustmentAccess,
      employeeId,
      adjustmentDate,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${company}/workforce/employees/${employeeId}/attendance/adjustments/review?workDate=${adjustmentDate}`,
    );
    raw.attendance.entries.splice(0);
    expect(result.value.attendance.entries).toHaveLength(1);
    expect(Object.isFrozen(result.value.current.schedule)).toBe(true);
    expect(Object.isFrozen(result.value.attendance.entries)).toBe(true);
  });
  it.each(["employee", "month", "status", "day", "correction", "sources"] as const)(
    "rejects substituted %s evidence inside the HTTP failure boundary",
    async (kind) => {
      const raw = adjustmentReviewDto();
      if (kind === "employee") raw.employee.id = identifier(1);
      if (kind === "month") raw.period.month = "2026-08";
      if (kind === "status") raw.period.status = "OPEN";
      if (kind === "day") raw.current.workDate = "2026-09-06";
      if (kind === "correction") raw.current.correctionId = identifier(2);
      if (kind === "sources") raw.current.evidenceIds = [identifier(3)];
      const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
      const result = await createWorkforceFeature({
        request,
      }).loadAttendanceAdjustmentReview.execute(
        adjustmentAccess,
        employeeId,
        adjustmentDate,
        signal(),
      );
      expect(result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    },
  );
  it("validates authorization, dates, operations and duplicate decisions before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    expect(
      (
        await feature.loadAttendanceAdjustmentReview.execute(
          { ...adjustmentAccess, permissions: [] },
          employeeId,
          adjustmentDate,
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await feature.loadAttendanceAdjustmentReview.execute(
          adjustmentAccess,
          employeeId,
          "2026-02-30",
          signal(),
        )
      ).ok,
    ).toBe(false);
    const change = adjustmentChange();
    for (const invalid of [
      { ...change, decisions: [...change.decisions, ...change.decisions] },
      { ...change, reviewRevision: "bad" },
      { ...change, expectedAdjustmentRevision: Number.MAX_SAFE_INTEGER },
      { ...change, clockOut: "2026-09-05T08:00:00Z" },
    ])
      expect(
        (
          await feature.adjustClosedAttendance.execute(
            adjustmentAccess,
            adjustmentOperation as OperationId,
            invalid,
            signal(),
          )
        ).ok,
      ).toBe(false);
    expect(request).not.toHaveBeenCalled();
  });
  it("sends both explicit null times and only reviewed command fields with one operation", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: adjustmentId, version: 1 });
    const change = adjustmentChange();
    expect(
      await createWorkforceFeature({ request }).adjustClosedAttendance.execute(
        adjustmentAccess,
        adjustmentOperation as OperationId,
        change,
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { id: adjustmentId, version: 1 } });
    expect(request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/workforce/employees/${employeeId}/attendance/adjustments`,
      method: "POST",
      operationId: adjustmentOperation,
      body: {
        workDate: adjustmentDate,
        reviewRevision: change.reviewRevision,
        clockIn: null,
        clockOut: null,
        breakMinutes: 0,
        decisions: change.decisions,
        reason: change.reason,
      },
    });
  });
  it("rejects substituted receipts, exact revisions and incomplete calendars", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: adjustmentId, version: 7 });
    const feature = createWorkforceFeature({ request });
    expect(
      (
        await feature.adjustClosedAttendance.execute(
          adjustmentAccess,
          adjustmentOperation as OperationId,
          adjustmentChange(),
          signal(),
        )
      ).ok,
    ).toBe(false);
    const raw = adjustmentDetailsDto();
    request.mockResolvedValue(raw);
    expect(
      (
        await feature.loadAttendanceAdjustment.execute(
          adjustmentAccess,
          employeeId,
          periodMonth,
          identifier(555),
          signal(),
        )
      ).ok,
    ).toBe(false);
    raw.snapshot.days.splice(1, 1);
    expect(
      (
        await feature.loadAttendanceAdjustment.execute(
          adjustmentAccess,
          employeeId,
          periodMonth,
          adjustmentId,
          signal(),
        )
      ).ok,
    ).toBe(false);
  });
  it("keeps history bounded and rejects a non-progressing cursor", async () => {
    const snapshot = workPeriodSnapshotDtoFixture();
    snapshot.adjustmentsSupported = true;
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(snapshot)
      .mockResolvedValueOnce({ items: [adjustmentDetailsDto().summary], nextCursor: "1" });
    const result = await createWorkforceFeature({ request }).loadAttendanceAdjustments.execute(
      adjustmentAccess,
      employeeId,
      periodMonth,
      null,
      signal(),
    );
    expect(result.ok).toBe(false);
    expect(request.mock.calls[1]?.[0].path).toContain("limit=25");
  });
  it("stops before history on old servers and propagates cancellation between acquisitions", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue(workPeriodSnapshotDtoFixture());
    const feature = createWorkforceFeature({ request });
    expect(
      await feature.loadAttendanceAdjustments.execute(
        adjustmentAccess,
        employeeId,
        periodMonth,
        null,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "attendance_adjustments_unavailable" } });
    expect(request).toHaveBeenCalledTimes(1);
    const abort = new AbortController();
    request.mockImplementation(async () => {
      abort.abort();
      return workPeriodSnapshotDtoFixture();
    });
    await expect(
      feature.loadAttendanceAdjustments.execute(
        adjustmentAccess,
        employeeId,
        periodMonth,
        null,
        abort.signal,
      ),
    ).rejects.toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
