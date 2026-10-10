import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveRequestQuery, LeaveStatus } from "../../domain/entities/leave-request";

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
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (query.employeeId !== null) parameters.set("employee", query.employeeId);
  if (query.status !== null) parameters.set("status", query.status);
  if (query.after !== null) parameters.set("after", query.after);
  if (historyAfter !== null) parameters.set("historyAfter", historyAfter);
  return parameters;
}
