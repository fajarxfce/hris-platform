import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LifecycleCase } from "../../domain/entities/lifecycle-case";
import type { OffboardingSchedule } from "../../domain/entities/offboarding-schedule";
export type OffboardingSchedulesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  items: readonly OffboardingSchedule[] | null;
  case: LifecycleCase | null;
  failure: Failure | null;
}>;
export const initialOffboardingSchedulesState: OffboardingSchedulesState = Object.freeze({
  stage: "loading",
  items: null,
  case: null,
  failure: null,
});
export type OffboardingScheduleState = Readonly<{
  stage: "loading" | "ready" | "updating" | "unavailable";
  details: OffboardingSchedule | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;
export const initialOffboardingScheduleState: OffboardingScheduleState = Object.freeze({
  stage: "loading",
  details: null,
  failure: null,
  updatesPaused: false,
});
export type OffboardingScheduleCommandState<T> = Readonly<{
  stage: "loading" | "unavailable" | "reviewing" | "saving" | "unconfirmed" | "conflict" | "saved";
  review: T | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
  scheduleId: string | null;
}>;
export function initialOffboardingScheduleCommand<T>(): OffboardingScheduleCommandState<T> {
  return {
    stage: "loading",
    review: null,
    failure: null,
    operationId: null,
    receipt: null,
    scheduleId: null,
  };
}
