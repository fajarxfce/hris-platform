import type { HttpClient } from "../../../../core/data/http/http-client";
import { headcountReportDto } from "../models/headcount-report-dto";
import type { HeadcountReportDataSource } from "./headcount-report-data-source";

export class HttpHeadcountReportDataSource implements HeadcountReportDataSource {
  constructor(private readonly http: HttpClient) {}

  async load(company: string, asOf: string, signal: AbortSignal) {
    const query = new URLSearchParams({ asOf });
    return headcountReportDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/headcount?${query}`,
        },
        signal,
      ),
    );
  }
}
