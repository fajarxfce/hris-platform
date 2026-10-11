import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { RosterDayChange } from "../entities/roster-day-change";
import { type Weekday, weekdays } from "../entities/schedule-assignment";
import type { ShiftReference } from "../entities/shift-reference";
import type { WeeklyScheduleChange } from "../entities/weekly-schedule-change";

export function isObservedCalendarVersion(version: number | null): boolean {
  return (
    version === null ||
    (Number.isSafeInteger(version) && version >= 0 && version < Number.MAX_SAFE_INTEGER)
  );
}
export function isShiftReference(reference: ShiftReference): boolean {
  return isUuid(reference.id) && Number.isSafeInteger(reference.version) && reference.version >= 0;
}
export function normalizeWeeklySchedule(input: WeeklyScheduleChange): Result<WeeklyScheduleChange> {
  if (
    !isUuid(input.employeeId) ||
    !isCalendarDate(input.effectiveFrom) ||
    !isObservedCalendarVersion(input.expectedVersion) ||
    !input.reason.trim() ||
    input.reason.length > 1000
  )
    return failed("invalid_schedule_change");
  const days: Partial<Record<Weekday, ShiftReference>> = {};
  for (const [day, reference] of Object.entries(input.days)) {
    if (!weekdays.includes(day as Weekday) || !reference || !isShiftReference(reference))
      return failed("invalid_schedule_change");
    days[day as Weekday] = Object.freeze({
      id: reference.id.toLowerCase(),
      version: reference.version,
    });
  }
  return success(
    Object.freeze({
      ...input,
      employeeId: input.employeeId.toLowerCase(),
      days: Object.freeze(days),
      reason: input.reason.trim(),
    }),
  );
}
export function normalizeRosterDay(input: RosterDayChange): Result<RosterDayChange> {
  if (
    !isUuid(input.employeeId) ||
    !isCalendarDate(input.workDate) ||
    !isObservedCalendarVersion(input.expectedVersion) ||
    !input.reason.trim() ||
    input.reason.length > 1000 ||
    (input.shift !== null && !isShiftReference(input.shift))
  )
    return failed("invalid_roster_change");
  return success(
    Object.freeze({
      ...input,
      employeeId: input.employeeId.toLowerCase(),
      shift:
        input.shift === null
          ? null
          : Object.freeze({ id: input.shift.id.toLowerCase(), version: input.shift.version }),
      reason: input.reason.trim(),
    }),
  );
}
/** This classification applies only before an unknown outcome has been observed. */
export function calendarChangeWasRejected(failure: Failure): boolean {
  return [
    "invalid_schedule_change",
    "invalid_roster_change",
    "stale_version",
    "data_conflict",
    "employee_unavailable",
    "employee_not_found",
    "shift_unavailable",
    "shift_changed",
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
