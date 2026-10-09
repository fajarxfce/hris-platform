import type { HttpClient } from "../../../../core/data/http/http-client";
import { headcountReportDto } from "../models/headcount-report-dto";
import type { HeadcountReportDataSource } from "./headcount-report-data-source";

export class HttpHeadcountReportDataSource implements HeadcountReportDataSource {
  constructor(private readonly http: HttpClient) {}

  async load(companies: readonly string[], asOf: string, signal: AbortSignal) {
    const query = new URLSearchParams({ companies: companies.join(","), asOf });
    return headcountReportDto.parse(
      await this.http.request(
        {
          path: `/api/v1/reports/headcount?${query}`,
        },
        signal,
      ),
    );
  }
}
