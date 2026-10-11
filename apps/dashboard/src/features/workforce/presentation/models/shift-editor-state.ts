import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ShiftDefinition } from "../../domain/entities/shift-definition";

export type ShiftEditorState = Readonly<{
  stage: "loading" | "editing" | "conflict" | "saving" | "unconfirmed" | "saved" | "unavailable";
  shift: ShiftDefinition | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialShiftEditorState: ShiftEditorState = {
  stage: "loading",
  shift: null,
  failure: null,
  receipt: null,
  operationId: null,
};
