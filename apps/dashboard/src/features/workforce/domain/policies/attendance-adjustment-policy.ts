import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, success } from "../../../../core/domain/result";
import type { AttendanceAdjustmentChange } from "../entities/attendance-adjustment-change";
import { attendanceChangeWasRejected, normalizeAttendanceCorrection } from "./attendance-policy";
import { canReadWorkPeriodSnapshot, isWorkPeriodMonth } from "./work-period-policy";

export const canAdjustClosedAttendance = (permissions: readonly string[]) =>
  canReadWorkPeriodSnapshot(permissions) && permissions.includes("attendance.correct");
export const isAttendanceAdjustmentDate = (date: string) =>
  isCalendarDate(date) && isWorkPeriodMonth(date.slice(0, 7));
export const isAttendanceAdjustmentCursor = (value: string) =>
  /^[1-9][0-9]{0,15}$/u.test(value) && Number.isSafeInteger(Number(value));

export function normalizeAttendanceAdjustment(change: AttendanceAdjustmentChange) {
  if (
    !isAttendanceAdjustmentDate(change.workDate) ||
    !/^[0-9a-f]{64}$/u.test(change.reviewRevision) ||
    !Number.isSafeInteger(change.expectedAdjustmentRevision) ||
    change.expectedAdjustmentRevision < 0 ||
    change.expectedAdjustmentRevision >= Number.MAX_SAFE_INTEGER ||
    change.decisions.length > 32 ||
    new Set(change.decisions.map((item) => item.eventId.toLowerCase())).size !==
      change.decisions.length ||
    change.decisions.some(
      (item) =>
        !isUuid(item.eventId) ||
        item.expectedVersion !== 0 ||
        !["ACCEPT", "REJECT"].includes(item.decision),
    )
  )
    return failed("invalid_attendance_adjustment");
  const correction = normalizeAttendanceCorrection({ ...change, expectedVersion: null });
  if (!correction.ok) return correction;
  return success(
    Object.freeze({
      ...change,
      employeeId: correction.value.employeeId,
      reason: correction.value.reason,
      decisions: Object.freeze(
        change.decisions.map((item) =>
          Object.freeze({ ...item, eventId: item.eventId.toLowerCase() }),
        ),
      ),
    }),
  );
}
export const attendanceAdjustmentAccessLost = (failure: Failure) =>
  [
    "access_denied",
    "company_access_denied",
    "employee_not_found",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "session_revoked",
    "authentication_required",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
  ].includes(failure.code);
export const attendanceAdjustmentReviewChanged = (failure: Failure) =>
  [
    "attendance_adjustment_changed",
    "attendance_adjustment_source_invalid",
    "attendance_adjustment_evidence_changed",
    "attendance_verification_required",
    "stale_version",
    "attendance_adjustment_requires_closed_period",
    "work_period_snapshot_unavailable",
  ].includes(failure.code);
export const attendanceAdjustmentCommandRejected = (failure: Failure) =>
  attendanceChangeWasRejected(failure) ||
  attendanceAdjustmentReviewChanged(failure) ||
  [
    "invalid_attendance_adjustment",
    "invalid_attendance_adjustment_date",
    "invalid_work_period_range",
    "attendance_adjustment_unchanged",
    "attendance_adjustment_version_exhausted",
  ].includes(failure.code);
