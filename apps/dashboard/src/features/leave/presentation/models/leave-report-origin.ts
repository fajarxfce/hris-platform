import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveReportQuery } from "../../../reporting/domain/entities/leave-report-request";
import {
  isLeaveReportPage,
  isLeaveReportRange,
} from "../../../reporting/domain/policies/leave-report-policy";
import type { LeaveStatus } from "../../domain/entities/leave-request";

/** Only a validated same-company report selection can become return context. */
export function leaveReportOrigin(
  parameters: URLSearchParams,
  company: CompanyId,
): LeaveReportQuery | null {
  if (parameters.get("company") !== company) return null;
  const from = parameters.get("reportFrom"),
    until = parameters.get("reportUntil");
  if (!from || !until || !isLeaveReportRange(from, until)) return null;
  const query = {
    from,
    until,
    status: parameters.get("reportStatus") as LeaveStatus | null,
    after: parameters.get("reportAfter"),
  };
  return isLeaveReportPage(company, query) ? Object.freeze(query) : null;
}

export function leaveReportReturn(company: CompanyId, origin: LeaveReportQuery): string {
  const query = new URLSearchParams({ company, from: origin.from, until: origin.until });
  if (origin.status !== null) query.set("status", origin.status);
  if (origin.after !== null) query.set("after", origin.after);
  return `/reports/leave/requests?${query}`;
}
