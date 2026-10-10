import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LeavePolicyDefinition } from "../../domain/entities/leave-policy-definition";

export type LeavePolicyEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  policy: LeavePolicyDefinition | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialLeavePolicyEditorState: LeavePolicyEditorState = Object.freeze({
  stage: "loading",
  policy: null,
  failure: null,
  receipt: null,
  operationId: null,
});
