import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { RosterBulkChange } from "../entities/roster-bulk-change";
import type { RosterBulkSelection } from "../entities/roster-bulk-selection";
import { calendarChangeWasRejected, isShiftReference } from "./calendar-change-policy";

export function normalizeRosterBulkSelection(
  input: RosterBulkSelection,
): Result<RosterBulkSelection> {
  if (
    input.employeeIds.length < 1 ||
    input.employeeIds.length > 50 ||
    input.dates.length < 1 ||
    input.dates.length > 31 ||
    input.employeeIds.length * input.dates.length > 200 ||
    input.employeeIds.some((id) => !isUuid(id)) ||
    input.dates.some((date) => !isCalendarDate(date) || date < "1900-01-01") ||
    !["SHIFT", "DAY_OFF", "CALENDAR"].includes(input.mode) ||
    (input.mode === "SHIFT") !== (input.shift !== null) ||
    (input.shift !== null && !isShiftReference(input.shift))
  )
    return failed("invalid_roster_bulk_selection");
  const employeeIds = input.employeeIds.map((id) => id.toLowerCase()).sort();
  const dates = [...input.dates].sort();
  if (
    new Set(employeeIds).size !== employeeIds.length ||
    new Set(dates).size !== dates.length ||
    Date.parse(`${dates.at(-1)}T00:00:00Z`) - Date.parse(`${dates[0]}T00:00:00Z`) > 30 * 86_400_000
  )
    return failed("invalid_roster_bulk_selection");
  return success(
    Object.freeze({
      employeeIds: Object.freeze(employeeIds),
      dates: Object.freeze(dates),
      mode: input.mode,
      shift:
        input.shift === null
          ? null
          : Object.freeze({ id: input.shift.id.toLowerCase(), version: input.shift.version }),
    }),
  );
}

export function normalizeRosterBulkChange(input: RosterBulkChange): Result<RosterBulkChange> {
  if (
    !/^[0-9a-f]{64}$/u.test(input.reviewRevision) ||
    !input.reason.trim() ||
    input.reason.length > 1000
  )
    return failed("invalid_roster_bulk_change");
  const selection = normalizeRosterBulkSelection(input.selection);
  return selection.ok
    ? success(Object.freeze({ ...input, selection: selection.value, reason: input.reason.trim() }))
    : selection;
}

/** Only a first, definite rejection permits replacing a command. */
export function rosterBulkCommandRejected(failure: Failure): boolean {
  return (
    calendarChangeWasRejected(failure) ||
    [
      "invalid_roster_bulk_selection",
      "invalid_roster_bulk_change",
      "roster_bulk_changed",
      "roster_bulk_unchanged",
    ].includes(failure.code)
  );
}

export function rosterBulkReviewChanged(failure: Failure): boolean {
  return [
    "roster_bulk_changed",
    "roster_bulk_unchanged",
    "shift_changed",
    "shift_unavailable",
    "employee_not_found",
    "employee_unavailable",
    "stale_version",
    "work_period_locked",
    "shift_timezone_gap",
  ].includes(failure.code);
}

export function rosterBulkAccessLost(failure: Failure): boolean {
  return [
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
  ].includes(failure.code);
}
