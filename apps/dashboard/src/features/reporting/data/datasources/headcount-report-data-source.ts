import type { HeadcountReportDto } from "../models/headcount-report-dto";

export interface HeadcountReportDataSource {
  load(company: string, asOf: string, signal: AbortSignal): Promise<HeadcountReportDto>;
}
