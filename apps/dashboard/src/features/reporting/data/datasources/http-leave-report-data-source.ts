import type { HttpClient } from "../../../../core/data/http/http-client";
import { leaveReportDto, leaveReportRequestPageDto } from "../models/leave-report-dto";
import type { LeaveReportDataSource } from "./leave-report-data-source";

export class HttpLeaveReportDataSource implements LeaveReportDataSource {
  constructor(private readonly http: HttpClient) {}

  async load(company: string, from: string, until: string, signal: AbortSignal) {
    const query = new URLSearchParams({ from, until });
    return leaveReportDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/leave?${query}`,
        },
        signal,
      ),
    );
  }

  async requests(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ from, until, limit: "50" });
    if (status !== null) query.set("status", status);
    if (after !== null) query.set("after", after);
    return leaveReportRequestPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/leave/requests?${query}`,
        },
        signal,
      ),
    );
  }
}
