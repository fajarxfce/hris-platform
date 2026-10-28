import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveReportQuery } from "../../../reporting/domain/entities/leave-report-request";
import type { LeaveRequestQuery, LeaveStatus } from "../../domain/entities/leave-request";
import type { LeaveCalendarOrigin } from "./leave-calendar-route";

export function leaveQuery(parameters: URLSearchParams, company: CompanyId): LeaveRequestQuery {
  if (parameters.has("company") && parameters.get("company") !== company)
    return { employeeId: null, status: null, after: null };
  return {
    employeeId: parameters.get("employee"),
    status: parameters.get("status") as LeaveStatus | null,
    after: parameters.get("after"),
  };
}
export function leaveParameters(
  company: CompanyId,
  query: LeaveRequestQuery,
  historyAfter: string | null = null,
  calendar: LeaveCalendarOrigin | null = null,
  report: LeaveReportQuery | null = null,
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (query.employeeId !== null) parameters.set("employee", query.employeeId);
  if (query.status !== null) parameters.set("status", query.status);
  if (query.after !== null) parameters.set("after", query.after);
  if (historyAfter !== null) parameters.set("historyAfter", historyAfter);
  if (calendar !== null) {
    parameters.set("calendarScope", calendar.scope);
    parameters.set("calendarMonth", calendar.month);
    parameters.set("calendarDate", calendar.date);
    if (calendar.after !== null) parameters.set("calendarAfter", calendar.after);
  }
  if (report !== null) {
    parameters.set("reportFrom", report.from);
    parameters.set("reportUntil", report.until);
    if (report.status !== null) parameters.set("reportStatus", report.status);
    if (report.after !== null) parameters.set("reportAfter", report.after);
  }
  return parameters;
}
