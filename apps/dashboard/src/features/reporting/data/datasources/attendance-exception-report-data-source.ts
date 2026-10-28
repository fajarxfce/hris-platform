import type { AttendanceExceptionReportDto } from "../models/attendance-exception-report-dto";

export interface AttendanceExceptionReportDataSource {
  load(
    company: string,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<AttendanceExceptionReportDto>;
}
