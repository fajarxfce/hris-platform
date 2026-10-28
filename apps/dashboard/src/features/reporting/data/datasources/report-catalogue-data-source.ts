import type { ReportCatalogueDto } from "../models/report-catalogue-dto";

export interface ReportCatalogueDataSource {
  load(company: string, signal: AbortSignal): Promise<ReportCatalogueDto>;
}
