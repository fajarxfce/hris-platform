import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LeaveBatchPage } from "../../domain/entities/leave-batch";
import type { LeaveBatchDetails } from "../../domain/entities/leave-batch-details";
import type { LeaveBatchReview } from "../../domain/entities/leave-batch-review";
export type LeaveBatchesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LeaveBatchPage | null;
  failure: Failure | null;
}>;
export const initialLeaveBatchesState: LeaveBatchesState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
export type LeaveBatchState = Readonly<{
  stage: "loading" | "ready" | "updating" | "unavailable";
  details: LeaveBatchDetails | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;
export const initialLeaveBatchState: LeaveBatchState = Object.freeze({
  stage: "loading",
  details: null,
  failure: null,
  updatesPaused: false,
});
export type LeaveBatchCommandState<T> = Readonly<{
  stage: "loading" | "unavailable" | "reviewing" | "saving" | "unconfirmed" | "conflict" | "saved";
  review: T | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
  batchId: string | null;
}>;
export function initialLeaveBatchCommand<T>(): LeaveBatchCommandState<T> {
  return {
    stage: "loading",
    review: null,
    failure: null,
    operationId: null,
    receipt: null,
    batchId: null,
  };
}
export type LeaveBatchStartState = Readonly<
  Omit<LeaveBatchCommandState<LeaveBatchReview>, "stage"> & {
    stage: LeaveBatchCommandState<LeaveBatchReview>["stage"] | "editing" | "preparing";
  }
>;
export const initialLeaveBatchStartState: LeaveBatchStartState = Object.freeze({
  ...initialLeaveBatchCommand<LeaveBatchReview>(),
  stage: "editing",
});
