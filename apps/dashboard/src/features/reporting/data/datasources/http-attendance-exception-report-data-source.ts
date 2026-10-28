import type { HttpClient } from "../../../../core/data/http/http-client";
import { attendanceExceptionReportDto } from "../models/attendance-exception-report-dto";
import type { AttendanceExceptionReportDataSource } from "./attendance-exception-report-data-source";

export class HttpAttendanceExceptionReportDataSource
  implements AttendanceExceptionReportDataSource
{
  constructor(private readonly http: HttpClient) {}

  async load(company: string, from: string, until: string, signal: AbortSignal) {
    const query = new URLSearchParams({ from, until });
    return attendanceExceptionReportDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/attendance-exceptions?${query}`,
        },
        signal,
      ),
    );
  }
}
