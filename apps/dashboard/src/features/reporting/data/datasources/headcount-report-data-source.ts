import type { HeadcountReportDto } from "../models/headcount-report-dto";

export interface HeadcountReportDataSource {
  load(
    companies: readonly string[],
    asOf: string,
    signal: AbortSignal,
  ): Promise<HeadcountReportDto>;
}
