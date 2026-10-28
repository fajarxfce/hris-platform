import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AttendanceExceptionReportRepository } from "../../domain/repositories/attendance-exception-report-repository";
import type { AttendanceExceptionReportDataSource } from "../datasources/attendance-exception-report-data-source";
import { toAttendanceExceptionReport } from "../mappers/attendance-exception-report-mapper";

export class RemoteAttendanceExceptionReportRepository
  implements AttendanceExceptionReportRepository
{
  constructor(private readonly source: AttendanceExceptionReportDataSource) {}

  load(company: CompanyId, from: string, until: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAttendanceExceptionReport(
        await this.source.load(company, from, until, signal),
        company,
        from,
        until,
      ),
    );
  }
}
