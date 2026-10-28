import type {
  AttendanceClosingEmployeePageDto,
  AttendanceClosingReportDto,
} from "../models/attendance-closing-report-dto";

export interface AttendanceClosingReportDataSource {
  employees(
    company: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<AttendanceClosingEmployeePageDto>;
  load(
    company: string,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<AttendanceClosingReportDto>;
}
