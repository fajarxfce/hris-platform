import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ExpenseClaimActionReview } from "../../domain/entities/expense-claim-action-review";
export type ExpenseClaimActionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "unavailable"
    | "saved";
  review: ExpenseClaimActionReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
  selectedLineId: string | null;
}>;
export const initialExpenseClaimActionState: ExpenseClaimActionState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
  selectedLineId: null,
});
