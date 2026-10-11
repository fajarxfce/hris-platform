import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { AttendanceCorrectionChange } from "../entities/attendance-correction-change";
import type { AttendanceReviewChange } from "../entities/attendance-review-change";
import type { CalendarRange } from "../entities/calendar-range";

export const canReadAttendance = (permissions: readonly string[]): boolean =>
  permissions.includes("workforce.read");
export const canReviewAttendance = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["attendance.verify", "attendance.team.verify"].includes(permission),
  );
export const canCorrectAttendance = (permissions: readonly string[]): boolean =>
  canReadAttendance(permissions) && permissions.includes("attendance.correct");

export function parseAttendanceRange(range: CalendarRange): Result<CalendarRange> {
  if (!isCalendarDate(range.from) || !isCalendarDate(range.until))
    return failed("invalid_attendance_range");
  const span =
    (Date.parse(`${range.until}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86_400_000;
  return span < 0 || span > 30
    ? failed("invalid_attendance_range")
    : success(Object.freeze({ ...range }));
}
export function normalizeAttendanceReview(
  change: AttendanceReviewChange,
): Result<AttendanceReviewChange> {
  if (
    !isUuid(change.entryId) ||
    !Number.isSafeInteger(change.version) ||
    change.version < 0 ||
    change.version >= Number.MAX_SAFE_INTEGER ||
    !["ACCEPT", "REJECT"].includes(change.decision) ||
    !change.reason.trim() ||
    change.reason.length > 1000
  )
    return failed("invalid_attendance_review");
  return success(
    Object.freeze({
      ...change,
      entryId: change.entryId.toLowerCase(),
      reason: change.reason.trim(),
    }),
  );
}
export function normalizeAttendanceCorrection(
  change: AttendanceCorrectionChange,
): Result<AttendanceCorrectionChange> {
  if (
    !isUuid(change.employeeId) ||
    !isCalendarDate(change.workDate) ||
    !change.reason.trim() ||
    change.reason.length > 1000 ||
    (change.expectedVersion !== null &&
      (!Number.isSafeInteger(change.expectedVersion) ||
        change.expectedVersion < 0 ||
        change.expectedVersion >= Number.MAX_SAFE_INTEGER)) ||
    !Number.isSafeInteger(change.breakMinutes) ||
    change.breakMinutes < 0 ||
    (change.clockIn === null) !== (change.clockOut === null)
  )
    return failed("invalid_attendance_correction");
  if (change.clockIn === null || change.clockOut === null) {
    if (change.breakMinutes !== 0) return failed("invalid_attendance_correction");
  } else {
    const start = utcInstantMicroseconds(change.clockIn);
    const end = utcInstantMicroseconds(change.clockOut);
    if (
      start === null ||
      end === null ||
      start % 60_000_000n !== 0n ||
      end % 60_000_000n !== 0n ||
      end <= start ||
      end - start > 86_400_000_000n ||
      BigInt(change.breakMinutes) * 60_000_000n >= end - start
    )
      return failed("invalid_attendance_correction");
  }
  return success(
    Object.freeze({
      ...change,
      employeeId: change.employeeId.toLowerCase(),
      reason: change.reason.trim(),
    }),
  );
}
/** A definitive rejection cannot undo an earlier ambiguous attempt. */
export function attendanceChangeWasRejected(failure: Failure): boolean {
  return [
    "invalid_attendance_review",
    "invalid_attendance_correction",
    "invalid_version",
    "invalid_operation_id",
    "stale_version",
    "data_conflict",
    "employee_not_found",
    "employee_unavailable",
    "attendance_not_found",
    "attendance_review_denied",
    "self_correction_denied",
    "attendance_sequence_conflict",
    "attendance_day_corrected",
    "attendance_verification_required",
    "future_attendance_correction",
    "work_period_locked",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
}
