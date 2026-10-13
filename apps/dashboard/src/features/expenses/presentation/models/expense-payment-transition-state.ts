import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
export type ExpensePaymentTransitionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "unavailable"
    | "saved";
  review: ExpensePaymentReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
  selectedItemId: string | null;
}>;
export const initialExpensePaymentTransitionState: ExpensePaymentTransitionState = {
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
  selectedItemId: null,
};
