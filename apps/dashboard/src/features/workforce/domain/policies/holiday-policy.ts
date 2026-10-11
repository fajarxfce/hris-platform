import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { HolidayRange } from "../entities/holiday-range";

export const canReadHolidays = (permissions: readonly string[]): boolean =>
  permissions.includes("company.read");
export const canManageHolidays = (permissions: readonly string[]): boolean =>
  canReadHolidays(permissions) && permissions.includes("workforce.manage");

/** Inclusive calendar dates: at most 366 days, independently of DST or browser timezone. */
export function validateHolidayRange(range: HolidayRange): Result<HolidayRange> {
  if (!isCalendarDate(range.from) || !isCalendarDate(range.until))
    return failed("invalid_calendar_range");
  const days =
    (Date.parse(`${range.until}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86_400_000;
  if (days < 0 || days > 365) return failed("invalid_calendar_range");
  return success(Object.freeze({ from: range.from, until: range.until }));
}
