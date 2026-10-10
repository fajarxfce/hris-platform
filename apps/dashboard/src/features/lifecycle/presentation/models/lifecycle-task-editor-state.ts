import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";

export type LifecycleTaskEditorState = Readonly<{
  stage: "editing" | "saving" | "unconfirmed" | "conflict" | "saved";
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialLifecycleTaskEditorState: LifecycleTaskEditorState = {
  stage: "editing",
  failure: null,
  operationId: null,
  receipt: null,
};
