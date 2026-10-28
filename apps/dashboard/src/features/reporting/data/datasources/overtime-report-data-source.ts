import type { OvertimeReportDto } from "../models/overtime-report-dto";

export interface OvertimeReportDataSource {
  load(
    company: string,
    from: string,
    until: string,
    status: string | null,
    signal: AbortSignal,
  ): Promise<OvertimeReportDto>;
}
