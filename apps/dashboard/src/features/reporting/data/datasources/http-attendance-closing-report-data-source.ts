import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  attendanceClosingEmployeePageDto,
  attendanceClosingReportDto,
} from "../models/attendance-closing-report-dto";
import type { AttendanceClosingReportDataSource } from "./attendance-closing-report-data-source";

export class HttpAttendanceClosingReportDataSource implements AttendanceClosingReportDataSource {
  constructor(private readonly http: HttpClient) {}
  async employees(company: string, month: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ month, limit: "50" });
    if (after !== null) query.set("after", after);
    return attendanceClosingEmployeePageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/attendance-closings/employees?${query}`,
        },
        signal,
      ),
    );
  }
  async load(company: string, from: string, until: string, signal: AbortSignal) {
    const query = new URLSearchParams({ from, until });
    return attendanceClosingReportDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/attendance-closings?${query}`,
        },
        signal,
      ),
    );
  }
}
