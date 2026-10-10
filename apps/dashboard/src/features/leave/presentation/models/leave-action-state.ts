import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LeaveRequestDetails } from "../../domain/entities/leave-request-details";

export type LeaveActionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  review: LeaveRequestDetails | null;
  phase: "request" | "cancellation";
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialLeaveActionState: LeaveActionState = Object.freeze({
  stage: "loading",
  review: null,
  phase: "request",
  failure: null,
  receipt: null,
  operationId: null,
});
