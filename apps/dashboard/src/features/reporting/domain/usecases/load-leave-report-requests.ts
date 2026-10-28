import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveReportQuery } from "../entities/leave-report-request";
import {
  canReadLeaveReport,
  isLeaveReportPage,
  isLeaveReportRange,
} from "../policies/leave-report-policy";
import type { LeaveReportRepository } from "../repositories/leave-report-repository";

export class LoadLeaveReportRequests {
  constructor(private readonly reports: LeaveReportRepository) {}
  execute(access: CompanyAccess, query: LeaveReportQuery, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLeaveReport(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isLeaveReportRange(query.from, query.until))
      return Promise.resolve({
        ok: false as const,
        failure: { code: "invalid_report_range", fields: {}, parameters: { maximumDays: "366" } },
      });
    if (!isLeaveReportPage(access.companyId, query)) return Promise.resolve(failed("invalid_page"));
    return this.reports.requests(access.companyId, Object.freeze({ ...query }), signal);
  }
}
