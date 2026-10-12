import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { failed, type Result, success } from "../../../../core/domain/result";
import { isEmployeeNumber } from "../../../people/domain/policies/employee-policy";
import type { WorkPeriodEmployeeSearch } from "../entities/work-period-employee-search";

export const canReadWorkPeriods = (permissions: readonly string[]): boolean =>
  permissions.some((value) => value === "workforce.read" || value === "workforce.close");

export const canReadWorkPeriodSnapshot = (permissions: readonly string[]): boolean =>
  permissions.some((value) =>
    ["workforce.read", "workforce.team.read", "attendance.self.record"].includes(value),
  );

export const isWorkPeriodMonth = (month: string): boolean =>
  /^\d{4}-\d{2}$/u.test(month) &&
  isCalendarDate(`${month}-01`) &&
  month >= "2000-01" &&
  month <= "2100-12";

export function workPeriodDates(month: string): readonly string[] {
  if (!isWorkPeriodMonth(month)) return [];
  const from = Date.parse(`${month}-01T00:00:00Z`);
  const count = new Date(
    Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5)), 0),
  ).getUTCDate();
  return Array.from({ length: count }, (_value, index) =>
    new Date(from + index * 86_400_000).toISOString().slice(0, 10),
  );
}

export function parseWorkPeriodEmployeeSearch(
  input: WorkPeriodEmployeeSearch,
): Result<WorkPeriodEmployeeSearch> {
  if (!isWorkPeriodMonth(input.month)) return failed("invalid_work_period_range");
  if (input.query.length > 120 || (input.after !== null && !isEmployeeNumber(input.after)))
    return failed("invalid_page");
  return success(Object.freeze({ ...input, query: input.query.trim() }));
}
