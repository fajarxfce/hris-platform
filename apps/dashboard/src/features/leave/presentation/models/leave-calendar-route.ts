import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import type { LeaveCalendarScope } from "../../domain/entities/leave-calendar";
import {
  isLeaveCalendarDate,
  isLeaveCalendarMonth,
} from "../../domain/policies/leave-calendar-policy";

export function leaveCalendarParameters(
  company: CompanyId,
  scope: LeaveCalendarScope,
  month: string | null,
  date: string | null = null,
  after: string | null = null,
): URLSearchParams {
  const query = new URLSearchParams({ company, scope });
  if (month !== null) query.set("month", month);
  if (date !== null) query.set("date", date);
  if (after !== null) query.set("after", after);
  return query;
}
export function leaveCalendarRoute(
  company: CompanyId,
  permissions: readonly string[],
  parameters: URLSearchParams,
) {
  const foreignCompany = parameters.has("company") && parameters.get("company") !== company;
  const fallback: LeaveCalendarScope = permissions.includes("leave.read") ? "COMPANY" : "TEAM";
  const selected = foreignCompany ? fallback : (parameters.get("scope") ?? fallback);
  const scope: LeaveCalendarScope | null =
    selected === "COMPANY" || selected === "TEAM" ? selected : null;
  const month = foreignCompany ? null : parameters.get("month"),
    date = foreignCompany ? null : parameters.get("date"),
    after = foreignCompany ? null : parameters.get("after");
  const query = leaveCalendarParameters(company, scope ?? fallback, month, date, after);
  return {
    scope,
    month,
    date,
    after,
    foreignCompany,
    canonical: parameters.has("company") && parameters.has("scope"),
    to: `/leave/calendar?${query}`,
  };
}

export type LeaveCalendarOrigin = Readonly<{
  scope: LeaveCalendarScope;
  month: string;
  date: string;
  after: string | null;
}>;

/** A same-company calendar selection, never an arbitrary return URL. */
export function leaveCalendarOrigin(
  parameters: URLSearchParams,
  company: CompanyId,
): LeaveCalendarOrigin | null {
  if (parameters.get("company") !== company) return null;
  const scope = parameters.get("calendarScope"),
    month = parameters.get("calendarMonth"),
    date = parameters.get("calendarDate"),
    after = parameters.get("calendarAfter");
  if (
    (scope !== "COMPANY" && scope !== "TEAM") ||
    month === null ||
    !isLeaveCalendarMonth(month) ||
    date === null ||
    !isLeaveCalendarDate(date) ||
    !date.startsWith(`${month}-`) ||
    (after !== null && !isUuid(after))
  )
    return null;
  return { scope, month, date, after };
}
