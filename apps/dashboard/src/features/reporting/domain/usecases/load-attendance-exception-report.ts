import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  canReadAttendanceReport,
  isAttendanceReportRange,
} from "../policies/attendance-report-policy";
import type { AttendanceExceptionReportRepository } from "../repositories/attendance-exception-report-repository";

export class LoadAttendanceExceptionReport {
  constructor(private readonly reports: AttendanceExceptionReportRepository) {}

  execute(access: CompanyAccess, from: string, until: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadAttendanceReport(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isAttendanceReportRange(from, until))
      return Promise.resolve(failed("invalid_report_range"));
    return this.reports.load(access.companyId, from, until, signal);
  }
}
