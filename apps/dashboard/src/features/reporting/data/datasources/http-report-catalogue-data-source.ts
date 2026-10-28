import type { HttpClient } from "../../../../core/data/http/http-client";
import { reportCatalogueDto } from "../models/report-catalogue-dto";
import type { ReportCatalogueDataSource } from "./report-catalogue-data-source";

export class HttpReportCatalogueDataSource implements ReportCatalogueDataSource {
  constructor(private readonly http: HttpClient) {}
  async load(company: string, signal: AbortSignal) {
    return reportCatalogueDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/catalogue`,
        },
        signal,
      ),
    );
  }
}
