import type { LeaveReportDto, LeaveReportRequestPageDto } from "../models/leave-report-dto";

export interface LeaveReportDataSource {
  requests(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveReportRequestPageDto>;
  load(company: string, from: string, until: string, signal: AbortSignal): Promise<LeaveReportDto>;
}
