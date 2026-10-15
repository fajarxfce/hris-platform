import type {
  OffboardingScheduleChangeDto,
  OffboardingScheduleCreationDto,
  OffboardingScheduleDto,
  OffboardingScheduleReceiptDto,
  OffboardingScheduleReviewDto,
  OffboardingSchedulesDto,
} from "../models/offboarding-schedule-dto";
export interface OffboardingScheduleDataSource {
  review(
    company: string,
    caseId: string,
    signal: AbortSignal,
  ): Promise<OffboardingScheduleReviewDto>;
  list(company: string, caseId: string, signal: AbortSignal): Promise<OffboardingSchedulesDto>;
  get(
    company: string,
    caseId: string,
    id: string,
    signal: AbortSignal,
  ): Promise<OffboardingScheduleDto>;
  schedule(
    company: string,
    caseId: string,
    operation: string,
    input: OffboardingScheduleCreationDto,
    signal: AbortSignal,
  ): Promise<OffboardingScheduleReceiptDto>;
  cancel(
    company: string,
    caseId: string,
    id: string,
    operation: string,
    input: OffboardingScheduleChangeDto,
    signal: AbortSignal,
  ): Promise<OffboardingScheduleReceiptDto>;
  recover(
    company: string,
    caseId: string,
    id: string,
    operation: string,
    input: OffboardingScheduleChangeDto,
    signal: AbortSignal,
  ): Promise<OffboardingScheduleReceiptDto>;
}
