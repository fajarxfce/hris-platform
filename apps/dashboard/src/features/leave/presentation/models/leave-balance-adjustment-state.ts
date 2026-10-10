import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LeaveLedger } from "../../domain/entities/leave-ledger";

export type LeaveBalanceAdjustmentState = Readonly<{
  stage: "loading" | "editing" | "saving" | "conflict" | "unconfirmed" | "saved" | "unavailable";
  review: LeaveLedger | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialLeaveBalanceAdjustmentState: LeaveBalanceAdjustmentState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
});
