import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LeaveAccrualSchedulePage } from "../../domain/entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleDetails } from "../../domain/entities/leave-accrual-schedule-details";
import type { LeaveAccrualScheduleReview } from "../../domain/entities/leave-accrual-schedule-review";
export type LeaveAccrualSchedulesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LeaveAccrualSchedulePage | null;
  failure: Failure | null;
}>;
export const initialLeaveAccrualSchedulesState: LeaveAccrualSchedulesState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
export type LeaveAccrualScheduleState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  details: LeaveAccrualScheduleDetails | null;
  failure: Failure | null;
}>;
export const initialLeaveAccrualScheduleState: LeaveAccrualScheduleState = Object.freeze({
  stage: "loading",
  details: null,
  failure: null,
});
export type LeaveAccrualScheduleStartState = Readonly<{
  stage:
    | "editing"
    | "preparing"
    | "reviewing"
    | "unavailable"
    | "saving"
    | "unconfirmed"
    | "conflict"
    | "saved";
  review: LeaveAccrualScheduleReview | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
  scheduleId: string | null;
}>;
export const initialLeaveAccrualScheduleStartState: LeaveAccrualScheduleStartState = Object.freeze({
  stage: "editing",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
  scheduleId: null,
});
