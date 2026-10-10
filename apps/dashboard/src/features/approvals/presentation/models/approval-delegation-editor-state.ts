import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ApprovalDelegation } from "../../domain/entities/approval-delegation";

export type ApprovalDelegationEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  delegation: ApprovalDelegation | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialApprovalDelegationEditorState: ApprovalDelegationEditorState = Object.freeze({
  stage: "loading",
  delegation: null,
  failure: null,
  receipt: null,
  operationId: null,
});
