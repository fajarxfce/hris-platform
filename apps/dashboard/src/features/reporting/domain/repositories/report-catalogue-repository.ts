import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ReportCatalogue } from "../entities/report-catalogue";

export interface ReportCatalogueRepository {
  load(company: CompanyId, signal: AbortSignal): Promise<Result<ReportCatalogue>>;
}
