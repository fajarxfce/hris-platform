import type {
  OnboardingScheduleChangeDto,
  OnboardingScheduleCreationDto,
  OnboardingScheduleDto,
  OnboardingScheduleReceiptDto,
  OnboardingScheduleReviewDto,
  OnboardingScheduleSelectionDto,
  OnboardingSchedulesDto,
} from "../models/onboarding-schedule-dto";
export interface OnboardingScheduleDataSource {
  review(
    company: string,
    employee: string,
    selection: OnboardingScheduleSelectionDto,
    signal: AbortSignal,
  ): Promise<OnboardingScheduleReviewDto>;
  list(company: string, employee: string, signal: AbortSignal): Promise<OnboardingSchedulesDto>;
  get(
    company: string,
    employee: string,
    id: string,
    signal: AbortSignal,
  ): Promise<OnboardingScheduleDto>;
  schedule(
    company: string,
    employee: string,
    operation: string,
    input: OnboardingScheduleCreationDto,
    signal: AbortSignal,
  ): Promise<OnboardingScheduleReceiptDto>;
  cancel(
    company: string,
    employee: string,
    id: string,
    operation: string,
    input: OnboardingScheduleChangeDto,
    signal: AbortSignal,
  ): Promise<OnboardingScheduleReceiptDto>;
  recover(
    company: string,
    employee: string,
    id: string,
    operation: string,
    input: OnboardingScheduleChangeDto,
    signal: AbortSignal,
  ): Promise<OnboardingScheduleReceiptDto>;
}
