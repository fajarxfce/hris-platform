import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPolicyEditorContext } from "../../domain/usecases/load-payroll-policy-editor";

export type PayrollPolicyEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "conflict" | "unconfirmed" | "saved" | "unavailable";
  context: PayrollPolicyEditorContext | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialPayrollPolicyEditorState: PayrollPolicyEditorState = {
  stage: "loading",
  context: null,
  failure: null,
  operationId: null,
  receipt: null,
};
