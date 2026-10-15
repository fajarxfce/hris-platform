import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type LeaveAccrualScheduleCreationDto,
  type LeaveAccrualScheduleQueryDto,
  leaveAccrualScheduleDetailsDto,
  leaveAccrualScheduleReviewDto,
  leaveAccrualSchedulesDto,
} from "../models/leave-accrual-schedule-dto";
import { leaveReceiptDto } from "../models/leave-receipt-dto";
import type { LeaveAccrualScheduleDataSource } from "./leave-accrual-schedule-data-source";
export class HttpLeaveAccrualScheduleDataSource implements LeaveAccrualScheduleDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(company: string, selection: LeaveAccrualScheduleQueryDto, signal: AbortSignal) {
    const query = new URLSearchParams({
      typeId: selection.typeId,
      firstPeriod: selection.firstPeriod,
      periods: String(selection.periods),
    });
    return leaveAccrualScheduleReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/accrual-schedules/review?${query}` },
        signal,
      ),
    );
  }
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveAccrualSchedulesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/accrual-schedules?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return leaveAccrualScheduleDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/accrual-schedules/${id}` },
        signal,
      ),
    );
  }
  async create(
    company: string,
    operation: string,
    input: LeaveAccrualScheduleCreationDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/accrual-schedules`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
