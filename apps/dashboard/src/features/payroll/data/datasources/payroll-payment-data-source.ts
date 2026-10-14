import type { PayrollPayablePageDto } from "../models/payroll-payable-dto";
import type { PayrollPaymentPageDto, PayrollPaymentReviewDto } from "../models/payroll-payment-dto";
import type { PayrollPaymentPreparationDto } from "../models/payroll-payment-preparation-dto";
import type { PayrollPaymentReconciliationDto } from "../models/payroll-payment-reconciliation-dto";
import type {
  PayrollPaymentTransitionDto,
  PayrollPaymentTransitionReceiptDto,
} from "../models/payroll-payment-transition-dto";
export interface PayrollPaymentDataSource {
  payables(
    company: string,
    from: string,
    until: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollPayablePageDto>;
  prepare(
    company: string,
    id: string,
    operation: string,
    command: PayrollPaymentPreparationDto,
    signal: AbortSignal,
  ): Promise<PayrollPaymentTransitionReceiptDto>;
  reconcile(
    company: string,
    id: string,
    operation: string,
    command: PayrollPaymentReconciliationDto,
    signal: AbortSignal,
  ): Promise<PayrollPaymentTransitionReceiptDto>;
  transition(
    company: string,
    id: string,
    operation: string,
    action: "release" | "cancel",
    command: PayrollPaymentTransitionDto,
    signal: AbortSignal,
  ): Promise<PayrollPaymentTransitionReceiptDto>;
  list(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollPaymentPageDto>;
  review(
    company: string,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollPaymentReviewDto>;
  worksheet(company: string, id: string, signal: AbortSignal): Promise<string>;
}
