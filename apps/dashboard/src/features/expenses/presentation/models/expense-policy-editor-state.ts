import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ExpensePolicyDefinition } from "../../domain/entities/expense-policy-definition";

export type ExpensePolicyEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  policy: ExpensePolicyDefinition | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialExpensePolicyEditorState: ExpensePolicyEditorState = Object.freeze({
  stage: "loading",
  policy: null,
  failure: null,
  receipt: null,
  operationId: null,
});
