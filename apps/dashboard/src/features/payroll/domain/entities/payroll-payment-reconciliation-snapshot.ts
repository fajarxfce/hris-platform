import type { PayrollPaymentReview } from "./payroll-payment-review";
import { type PayrollPaymentSnapshot, payrollPaymentSnapshot } from "./payroll-payment-snapshot";
export type PayrollPaymentReconciliationSnapshot = PayrollPaymentSnapshot &
  Readonly<{ releasedAt: string | null; pendingItemIds: readonly string[] }>;
export function payrollPaymentReconciliationSnapshot(
  review: PayrollPaymentReview,
): PayrollPaymentReconciliationSnapshot {
  return Object.freeze({
    ...payrollPaymentSnapshot(review),
    releasedAt: review.batch.releasedAt,
    pendingItemIds: Object.freeze(
      review.batch.items.filter((item) => item.status === "PENDING").map((item) => item.id),
    ),
  });
}
