import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveStatus } from "../../../leave/domain/entities/leave-request";
import type { LeaveReportQuery } from "../../domain/entities/leave-report-request";

export function leaveReportQuery(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): LeaveReportQuery {
  const empty = { from: `${today.slice(0, 4)}-01-01`, until: today, status: null, after: null };
  if (parameters.has("company") && parameters.get("company") !== company) return empty;
  return {
    from: parameters.get("from") ?? empty.from,
    until: parameters.get("until") ?? today,
    status: parameters.get("status") as LeaveStatus | null,
    after: parameters.get("after"),
  };
}

export function leaveReportParameters(
  company: CompanyId,
  query: LeaveReportQuery,
): URLSearchParams {
  const result = new URLSearchParams({ company, from: query.from, until: query.until });
  if (query.status !== null) result.set("status", query.status);
  if (query.after !== null) result.set("after", query.after);
  return result;
}
