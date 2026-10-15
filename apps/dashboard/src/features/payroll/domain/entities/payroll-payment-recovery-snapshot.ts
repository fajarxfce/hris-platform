import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollPaymentRecoveryReview } from "./payroll-payment-recovery-review";

/** Retain only the facts needed to confirm or replay this specific observation. */
export type PayrollPaymentRecoverySnapshot = Readonly<{
  companyId: CompanyId;
  assessmentId: string;
  assessmentRevision: number;
  paymentVersion: number;
  recoverable: string;
  earliestConfirmationAt: string;
  canConfirm: boolean;
}>;

export const payrollPaymentRecoverySnapshot = (
  review: PayrollPaymentRecoveryReview,
): PayrollPaymentRecoverySnapshot =>
  Object.freeze({
    companyId: review.companyId,
    assessmentId: review.assessmentId,
    assessmentRevision: review.payments.settlement.assessmentRevision,
    paymentVersion: review.payments.version,
    recoverable: review.payments.settlement.recoverable,
    earliestConfirmationAt: review.earliestConfirmationAt,
    canConfirm: review.canConfirm,
  });
