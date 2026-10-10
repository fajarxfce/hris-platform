import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ApprovalRequest } from "../../domain/entities/approval-request";

export type ApprovalReassignmentState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  request: ApprovalRequest | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialApprovalReassignmentState: ApprovalReassignmentState = Object.freeze({
  stage: "loading",
  request: null,
  failure: null,
  receipt: null,
  operationId: null,
});
