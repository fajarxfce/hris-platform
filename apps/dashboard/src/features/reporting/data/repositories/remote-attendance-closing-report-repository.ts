import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AttendanceClosingEmployeeQuery } from "../../domain/entities/attendance-closing-report";
import type { AttendanceClosingReportRepository } from "../../domain/repositories/attendance-closing-report-repository";
import type { AttendanceClosingReportDataSource } from "../datasources/attendance-closing-report-data-source";
import {
  toAttendanceClosingEmployeePage,
  toAttendanceClosingReport,
} from "../mappers/attendance-closing-report-mapper";

export class RemoteAttendanceClosingReportRepository implements AttendanceClosingReportRepository {
  constructor(private readonly source: AttendanceClosingReportDataSource) {}
  employees(company: CompanyId, query: AttendanceClosingEmployeeQuery, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAttendanceClosingEmployeePage(
        await this.source.employees(company, query.month, query.after, signal),
        company,
        query,
      ),
    );
  }
  load(company: CompanyId, from: string, until: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAttendanceClosingReport(
        await this.source.load(company, from, until, signal),
        company,
        from,
        until,
      ),
    );
  }
}
