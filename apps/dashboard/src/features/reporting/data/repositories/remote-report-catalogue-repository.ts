import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ReportCatalogueRepository } from "../../domain/repositories/report-catalogue-repository";
import type { ReportCatalogueDataSource } from "../datasources/report-catalogue-data-source";
import { toReportCatalogue } from "../mappers/report-catalogue-mapper";

export class RemoteReportCatalogueRepository implements ReportCatalogueRepository {
  constructor(private readonly source: ReportCatalogueDataSource) {}
  load(company: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toReportCatalogue(await this.source.load(company, signal), company),
    );
  }
}
