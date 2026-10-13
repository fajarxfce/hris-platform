import type { ExpensePaymentBatch } from "./expense-payment-batch";
import type { ExpensePaymentReview } from "./expense-payment-review";
export type ExpensePaymentSnapshot = Readonly<
  Pick<ExpensePaymentBatch, "id" | "companyId" | "version" | "status"> &
    Pick<ExpensePaymentReview, "availableActions">
>;
export function expensePaymentSnapshot(review: ExpensePaymentReview): ExpensePaymentSnapshot {
  return Object.freeze({
    id: review.batch.id,
    companyId: review.batch.companyId,
    version: review.batch.version,
    status: review.batch.status,
    availableActions: Object.freeze([...review.availableActions]),
  });
}
