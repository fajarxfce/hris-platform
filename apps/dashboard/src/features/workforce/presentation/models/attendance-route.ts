export const attendancePath = "/workforce/attendance";
export const employeeAttendancePath = (employee: string): string =>
  `${attendancePath}/${encodeURIComponent(employee)}`;
export const attendanceDayPath = (employee: string, date: string): string =>
  `${employeeAttendancePath(employee)}/days/${encodeURIComponent(date)}`;

import type { CompanyId } from "../../../../core/domain/identifiers";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import { calendarParameters } from "./calendar-route";

export const attendanceCorrectionHistoryPath = (employee: string, date: string): string =>
  `${attendanceDayPath(employee, date)}/history`;

export function attendanceHistoryParameters(
  company: CompanyId,
  range: CalendarRange,
  after: string | null = null,
  change: string | null = null,
): URLSearchParams {
  const parameters = calendarParameters(company, range);
  if (after !== null) parameters.set("after", after);
  if (change !== null) parameters.set("change", change);
  return parameters;
}
