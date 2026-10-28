import type {
  ReportFilterReceiptDto,
  SavedReportFilterDto,
  SavedReportFiltersDto,
  SaveReportFilterDto,
} from "../models/saved-report-filter-dto";
export interface SavedReportFilterDataSource {
  list(company: string, signal: AbortSignal): Promise<SavedReportFiltersDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<SavedReportFilterDto>;
  save(
    company: string,
    id: string,
    operation: string,
    input: SaveReportFilterDto,
    signal: AbortSignal,
  ): Promise<ReportFilterReceiptDto>;
  delete(
    company: string,
    id: string,
    operation: string,
    expectedVersion: number,
    signal: AbortSignal,
  ): Promise<ReportFilterReceiptDto>;
}
