import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type AttendanceAdjustmentCommandDto,
  attendanceAdjustmentDetailsDto,
  attendanceAdjustmentPageDto,
  attendanceAdjustmentReviewDto,
} from "../models/attendance-adjustment-dto";
import { attendanceReceiptDto } from "../models/attendance-dto";
import type { AttendanceAdjustmentDataSource } from "./attendance-adjustment-data-source";

export class HttpAttendanceAdjustmentDataSource implements AttendanceAdjustmentDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(company: string, employee: string, date: string, signal: AbortSignal) {
    const query = new URLSearchParams({ workDate: date });
    return attendanceAdjustmentReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/adjustments/review?${query}`,
        },
        signal,
      ),
    );
  }
  async history(
    company: string,
    employee: string,
    month: string,
    before: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ month, limit: "25" });
    if (before !== null) query.set("before", before);
    return attendanceAdjustmentPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/adjustments?${query}`,
        },
        signal,
      ),
    );
  }
  async details(company: string, employee: string, id: string, signal: AbortSignal) {
    return attendanceAdjustmentDetailsDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/adjustments/${id}`,
        },
        signal,
      ),
    );
  }
  async adjust(
    company: string,
    employee: string,
    operation: string,
    body: AttendanceAdjustmentCommandDto,
    signal: AbortSignal,
  ) {
    return attendanceReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/adjustments`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
