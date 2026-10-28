import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadLeaveReport, isLeaveReportRange } from "../policies/leave-report-policy";
import type { LeaveReportRepository } from "../repositories/leave-report-repository";

export class LoadLeaveReport {
  constructor(private readonly reports: LeaveReportRepository) {}

  execute(access: CompanyAccess, from: string, until: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLeaveReport(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isLeaveReportRange(from, until))
      return Promise.resolve({
        ok: false as const,
        failure: { code: "invalid_report_range", fields: {}, parameters: { maximumDays: "366" } },
      });
    return this.reports.load(access.companyId, from, until, signal);
  }
}
