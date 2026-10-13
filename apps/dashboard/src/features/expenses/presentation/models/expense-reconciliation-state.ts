import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ExpensePaymentOutcome } from "../../domain/entities/expense-payment-outcome";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
export type ExpenseReconciliationState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "unavailable"
    | "saved";
  review: ExpensePaymentReview | null;
  results: readonly ExpensePaymentOutcome[];
  failure: Failure | null;
  entryFailure: Failure | null;
  entryDirty: boolean;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
  selectedItemId: string | null;
}>;
export const initialExpenseReconciliationState: ExpenseReconciliationState = {
  stage: "loading",
  review: null,
  results: [],
  failure: null,
  entryFailure: null,
  entryDirty: false,
  operationId: null,
  receipt: null,
  selectedItemId: null,
};
