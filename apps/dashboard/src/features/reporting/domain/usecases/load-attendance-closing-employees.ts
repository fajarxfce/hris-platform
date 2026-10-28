import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceClosingEmployeeQuery } from "../entities/attendance-closing-report";
import {
  attendanceClosingReportRangeParameters,
  canReadAttendanceClosingReport,
  isAttendanceClosingEmployeePage,
  isAttendanceClosingReportRange,
} from "../policies/attendance-closing-report-policy";
import type { AttendanceClosingReportRepository } from "../repositories/attendance-closing-report-repository";
export class LoadAttendanceClosingEmployees {
  constructor(private readonly reports: AttendanceClosingReportRepository) {}
  execute(access: CompanyAccess, query: AttendanceClosingEmployeeQuery, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadAttendanceClosingReport(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isAttendanceClosingReportRange(query.month, query.month))
      return Promise.resolve({
        ok: false as const,
        failure: {
          code: "invalid_report_month_range",
          fields: {},
          parameters: attendanceClosingReportRangeParameters,
        },
      });
    if (!isAttendanceClosingEmployeePage(access.companyId, query))
      return Promise.resolve(failed("invalid_page"));
    return this.reports.employees(access.companyId, Object.freeze({ ...query }), signal);
  }
}
