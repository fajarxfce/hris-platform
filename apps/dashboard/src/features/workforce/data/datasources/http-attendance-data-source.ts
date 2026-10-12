import type { HttpClient } from "../../../../core/data/http/http-client";
import { attendanceCorrectionPageDto } from "../models/attendance-correction-page-dto";
import {
  type AttendanceCorrectionChangeDto,
  type AttendanceReviewDto,
  attendanceDaysDto,
  attendanceReceiptDto,
} from "../models/attendance-dto";
import {
  type AttendanceExceptionSearchDto,
  attendanceExceptionPageDto,
} from "../models/attendance-exception-dto";
import type { CalendarRangeDto } from "../models/calendar-dto";
import type { AttendanceDataSource } from "./attendance-data-source";

export class HttpAttendanceDataSource implements AttendanceDataSource {
  constructor(private readonly http: HttpClient) {}
  async exceptions(company: string, search: AttendanceExceptionSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ from: search.from, until: search.until, limit: "50" });
    if (search.kind !== null) query.set("kind", search.kind);
    if (search.after !== null) query.set("after", search.after);
    return attendanceExceptionPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/attendance/exceptions?${query}` },
        signal,
      ),
    );
  }
  async corrections(
    company: string,
    employee: string,
    workDate: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ workDate, limit: "50" });
    if (after !== null) query.set("after", after);
    return attendanceCorrectionPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/corrections?${query}`,
        },
        signal,
      ),
    );
  }
  async days(company: string, employee: string, range: CalendarRangeDto, signal: AbortSignal) {
    const query = new URLSearchParams({ from: range.from, until: range.until });
    return attendanceDaysDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/attendance?${query}`,
        },
        signal,
      ),
    );
  }
  async review(
    company: string,
    id: string,
    operation: string,
    body: AttendanceReviewDto,
    signal: AbortSignal,
  ) {
    return attendanceReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/attendance/${id}/review`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async correct(
    company: string,
    employee: string,
    operation: string,
    body: AttendanceCorrectionChangeDto,
    signal: AbortSignal,
  ) {
    return attendanceReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/corrections`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
