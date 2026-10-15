import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { Employee } from "../../../people/domain/entities/employee";
import type { OnboardingSchedule } from "../../domain/entities/onboarding-schedule";
import type { OnboardingScheduleReview } from "../../domain/entities/onboarding-schedule-review";
export type OnboardingSchedulesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  items: readonly OnboardingSchedule[] | null;
  employee: Employee | null;
  failure: Failure | null;
}>;
export const initialOnboardingSchedulesState: OnboardingSchedulesState = Object.freeze({
  stage: "loading",
  items: null,
  employee: null,
  failure: null,
});
export type OnboardingScheduleState = Readonly<{
  stage: "loading" | "ready" | "updating" | "unavailable";
  details: OnboardingSchedule | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;
export const initialOnboardingScheduleState: OnboardingScheduleState = Object.freeze({
  stage: "loading",
  details: null,
  failure: null,
  updatesPaused: false,
});
export type OnboardingScheduleCommandState<T> = Readonly<{
  stage: "loading" | "unavailable" | "reviewing" | "saving" | "unconfirmed" | "conflict" | "saved";
  review: T | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
  scheduleId: string | null;
}>;
export function initialOnboardingScheduleCommand<T>(): OnboardingScheduleCommandState<T> {
  return {
    stage: "loading",
    review: null,
    failure: null,
    operationId: null,
    receipt: null,
    scheduleId: null,
  };
}
export type OnboardingScheduleStartState = Readonly<
  Omit<OnboardingScheduleCommandState<OnboardingScheduleReview>, "stage"> & {
    stage:
      | OnboardingScheduleCommandState<OnboardingScheduleReview>["stage"]
      | "editing"
      | "preparing";
    employee: Employee | null;
  }
>;
export const initialOnboardingScheduleStartState: OnboardingScheduleStartState = Object.freeze({
  ...initialOnboardingScheduleCommand<OnboardingScheduleReview>(),
  employee: null,
});
