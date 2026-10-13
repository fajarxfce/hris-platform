import type { ExpensePaymentReview } from "./expense-payment-review";
import { type ExpensePaymentSnapshot, expensePaymentSnapshot } from "./expense-payment-snapshot";
export type ExpenseReconciliationSnapshot = ExpensePaymentSnapshot &
  Readonly<{ releasedAt: string | null; pendingItemIds: readonly string[] }>;
export function expenseReconciliationSnapshot(
  review: ExpensePaymentReview,
): ExpenseReconciliationSnapshot {
  return Object.freeze({
    ...expensePaymentSnapshot(review),
    releasedAt: review.batch.releasedAt,
    pendingItemIds: Object.freeze(
      review.batch.items.filter((item) => item.status === "PENDING").map((item) => item.id),
    ),
  });
}
