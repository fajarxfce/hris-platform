import type { HttpClient } from "../../../../core/data/http/http-client";
import { overtimeReportDto } from "../models/overtime-report-dto";
import type { OvertimeReportDataSource } from "./overtime-report-data-source";

export class HttpOvertimeReportDataSource implements OvertimeReportDataSource {
  constructor(private readonly http: HttpClient) {}
  async load(
    company: string,
    from: string,
    until: string,
    status: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ from, until });
    if (status !== null) query.set("status", status);
    return overtimeReportDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${encodeURIComponent(company)}/reports/overtime?${query}` },
        signal,
      ),
    );
  }
}
