import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { failed, type Result, success } from "../../../../core/domain/result";
import { validateEmployeeSearch } from "../../../people/domain/policies/employee-policy";
import type { CalendarRange } from "../entities/calendar-range";
import type { WorkforceEmployeeSearch } from "../entities/workforce-employee-search";

export const canReadCalendars = (permissions: readonly string[]): boolean =>
  permissions.includes("workforce.read");
export const canManageCalendars = (permissions: readonly string[]): boolean =>
  canReadCalendars(permissions) && permissions.includes("workforce.manage");

export function parseCalendarRange(range: CalendarRange): Result<CalendarRange> {
  if (!isCalendarDate(range.from) || !isCalendarDate(range.until))
    return failed("invalid_calendar_range");
  const span =
    (Date.parse(`${range.until}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86_400_000;
  if (span < 0 || span > 61) return failed("invalid_calendar_range");
  return success(Object.freeze({ ...range }));
}

export function parseWorkforceEmployeeSearch(
  search: WorkforceEmployeeSearch,
): Result<WorkforceEmployeeSearch> {
  const failure = validateEmployeeSearch(search);
  return failure
    ? { ok: false, failure }
    : success(Object.freeze({ ...search, query: search.query.trim() }));
}
