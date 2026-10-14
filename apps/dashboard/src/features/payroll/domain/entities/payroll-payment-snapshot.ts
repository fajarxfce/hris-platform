import type { PayrollPaymentBatch } from "./payroll-payment-batch";
import type { PayrollPaymentReview } from "./payroll-payment-review";
export type PayrollPaymentSnapshot = Readonly<
  Pick<PayrollPaymentBatch, "id" | "companyId" | "version" | "status"> &
    Pick<PayrollPaymentReview, "availableActions">
>;
export function payrollPaymentSnapshot(review: PayrollPaymentReview): PayrollPaymentSnapshot {
  return Object.freeze({
    id: review.batch.id,
    companyId: review.batch.companyId,
    version: review.batch.version,
    status: review.batch.status,
    availableActions: Object.freeze([...review.availableActions]),
  });
}
