import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { CompensationEditorContext } from "../../domain/usecases/load-compensation-editor";

export type CompensationEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "conflict" | "unconfirmed" | "saved" | "unavailable";
  context: CompensationEditorContext | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialCompensationEditorState: CompensationEditorState = {
  stage: "loading",
  context: null,
  failure: null,
  operationId: null,
  receipt: null,
};
