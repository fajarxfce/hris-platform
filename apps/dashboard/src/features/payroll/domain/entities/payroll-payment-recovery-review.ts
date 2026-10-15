import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollPaymentProgress } from "./payroll-payment-progress";
import type { PayrollPaymentRecovery } from "./payroll-payment-recovery";

export type PayrollPaymentRecoveryBlocker =
  | "NOT_OVERPAID"
  | "PENDING_PAYMENT"
  | "RECOVERY_LIMIT"
  | "BENEFICIARY";

export type PayrollPaymentRecoveryReview = Readonly<{
  companyId: CompanyId;
  assessmentId: string;
  employmentId: string;
  employeeNumber: string;
  employeeName: string;
  taxMonth: string;
  publishedAt: string;
  payments: PayrollPaymentProgress;
  recoveries: readonly PayrollPaymentRecovery[];
  earliestConfirmationAt: string;
  canConfirm: boolean;
  blockers: readonly PayrollPaymentRecoveryBlocker[];
}>;
