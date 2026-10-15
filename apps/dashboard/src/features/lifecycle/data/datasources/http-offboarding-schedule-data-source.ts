import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type OffboardingScheduleChangeDto,
  type OffboardingScheduleCreationDto,
  offboardingScheduleDto,
  offboardingScheduleReceiptDto,
  offboardingScheduleReviewDto,
  offboardingSchedulesDto,
} from "../models/offboarding-schedule-dto";
import type { OffboardingScheduleDataSource } from "./offboarding-schedule-data-source";
export class HttpOffboardingScheduleDataSource implements OffboardingScheduleDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(company: string, caseId: string, signal: AbortSignal) {
    return offboardingScheduleReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/cases/${caseId}/offboarding-schedules/review`,
        },
        signal,
      ),
    );
  }
  async list(company: string, caseId: string, signal: AbortSignal) {
    return offboardingSchedulesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/lifecycle/cases/${caseId}/offboarding-schedules` },
        signal,
      ),
    );
  }
  async get(company: string, caseId: string, id: string, signal: AbortSignal) {
    return offboardingScheduleDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/cases/${caseId}/offboarding-schedules/${id}`,
        },
        signal,
      ),
    );
  }
  async schedule(
    company: string,
    caseId: string,
    operation: string,
    input: OffboardingScheduleCreationDto,
    signal: AbortSignal,
  ) {
    return offboardingScheduleReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/cases/${caseId}/offboarding-schedules`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async cancel(
    company: string,
    caseId: string,
    id: string,
    operation: string,
    input: OffboardingScheduleChangeDto,
    signal: AbortSignal,
  ) {
    return offboardingScheduleReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/cases/${caseId}/offboarding-schedules/${id}/cancel`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async recover(
    company: string,
    caseId: string,
    id: string,
    operation: string,
    input: OffboardingScheduleChangeDto,
    signal: AbortSignal,
  ) {
    return offboardingScheduleReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/cases/${caseId}/offboarding-schedules/${id}/recover`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
