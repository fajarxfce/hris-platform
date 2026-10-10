import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { EmploymentDetails } from "../../domain/entities/employment-details";

export type EmploymentEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "saved" | "conflict" | "unavailable";
  details: EmploymentDetails | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
  savedDate: string | null;
}>;
export const initialEmploymentEditorState: EmploymentEditorState = Object.freeze({
  stage: "loading",
  details: null,
  failure: null,
  receipt: null,
  operationId: null,
  savedDate: null,
});
