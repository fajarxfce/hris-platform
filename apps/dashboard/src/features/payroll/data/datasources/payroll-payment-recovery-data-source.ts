import type {
  ConfirmPayrollPaymentRecoveryDto,
  PayrollPaymentRecoveryReviewDto,
} from "../models/payroll-payment-recovery-dto";
import type { PayrollPaymentTransitionReceiptDto } from "../models/payroll-payment-transition-dto";

export interface PayrollPaymentRecoveryDataSource {
  review(
    company: string,
    assessment: string,
    signal: AbortSignal,
  ): Promise<PayrollPaymentRecoveryReviewDto>;
  confirm(
    company: string,
    assessment: string,
    id: string,
    operation: string,
    input: ConfirmPayrollPaymentRecoveryDto,
    signal: AbortSignal,
  ): Promise<PayrollPaymentTransitionReceiptDto>;
}
