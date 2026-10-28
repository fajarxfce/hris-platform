import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  attendanceClosingReportRangeParameters,
  canReadAttendanceClosingReport,
  isAttendanceClosingReportRange,
} from "../policies/attendance-closing-report-policy";
import type { AttendanceClosingReportRepository } from "../repositories/attendance-closing-report-repository";

export class LoadAttendanceClosingReport {
  constructor(private readonly reports: AttendanceClosingReportRepository) {}
  execute(access: CompanyAccess, from: string, until: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadAttendanceClosingReport(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isAttendanceClosingReportRange(from, until))
      return Promise.resolve({
        ok: false as const,
        failure: {
          code: "invalid_report_month_range",
          fields: {},
          parameters: attendanceClosingReportRangeParameters,
        },
      });
    return this.reports.load(access.companyId, from, until, signal);
  }
}
