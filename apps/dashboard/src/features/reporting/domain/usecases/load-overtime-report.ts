import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { overtimeStatuses } from "../../../workforce/domain/entities/overtime-request";
import {
  canReadOvertimeReport,
  isOvertimeReportRange,
  overtimeReportRangeParameters,
} from "../policies/overtime-report-policy";
import type { OvertimeReportRepository } from "../repositories/overtime-report-repository";

export class LoadOvertimeReport {
  constructor(private readonly reports: OvertimeReportRepository) {}
  execute(
    access: CompanyAccess,
    from: string,
    until: string,
    status: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadOvertimeReport(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isOvertimeReportRange(from, until))
      return Promise.resolve({
        ok: false as const,
        failure: {
          code: "invalid_report_range",
          fields: {},
          parameters: overtimeReportRangeParameters,
        },
      });
    const selected = status === null ? null : overtimeStatuses.find((value) => value === status);
    if (selected === undefined) return Promise.resolve(failed("invalid_report_status"));
    return this.reports.load(access.companyId, from, until, selected, signal);
  }
}
