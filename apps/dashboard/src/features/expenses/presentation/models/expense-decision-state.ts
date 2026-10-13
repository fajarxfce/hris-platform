import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
export type ExpenseDecisionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "unavailable"
    | "saved";
  review: ExpenseSubmission | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
  selectedLineId: string | null;
}>;
export const initialExpenseDecisionState: ExpenseDecisionState = {
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
  selectedLineId: null,
};
