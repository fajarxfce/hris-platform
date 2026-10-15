import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type OnboardingScheduleChangeDto,
  type OnboardingScheduleCreationDto,
  type OnboardingScheduleSelectionDto,
  onboardingScheduleDto,
  onboardingScheduleReceiptDto,
  onboardingScheduleReviewDto,
  onboardingSchedulesDto,
} from "../models/onboarding-schedule-dto";
import type { OnboardingScheduleDataSource } from "./onboarding-schedule-data-source";
export class HttpOnboardingScheduleDataSource implements OnboardingScheduleDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(
    company: string,
    employee: string,
    selection: OnboardingScheduleSelectionDto,
    signal: AbortSignal,
  ) {
    return onboardingScheduleReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employees/${employee}/onboarding-schedules/review?${new URLSearchParams(selection)}`,
        },
        signal,
      ),
    );
  }
  async list(company: string, employee: string, signal: AbortSignal) {
    return onboardingSchedulesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/employees/${employee}/onboarding-schedules` },
        signal,
      ),
    );
  }
  async get(company: string, employee: string, id: string, signal: AbortSignal) {
    return onboardingScheduleDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/employees/${employee}/onboarding-schedules/${id}` },
        signal,
      ),
    );
  }
  async schedule(
    company: string,
    employee: string,
    operation: string,
    input: OnboardingScheduleCreationDto,
    signal: AbortSignal,
  ) {
    return onboardingScheduleReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employees/${employee}/onboarding-schedules`,
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
    employee: string,
    id: string,
    operation: string,
    input: OnboardingScheduleChangeDto,
    signal: AbortSignal,
  ) {
    return onboardingScheduleReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employees/${employee}/onboarding-schedules/${id}/cancel`,
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
    employee: string,
    id: string,
    operation: string,
    input: OnboardingScheduleChangeDto,
    signal: AbortSignal,
  ) {
    return onboardingScheduleReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employees/${employee}/onboarding-schedules/${id}/recover`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
