import type { PayrollPaymentBatchId } from "./payroll-payment-batch";
import type { PayrollPaymentTransition } from "./payroll-payment-transition";
export type PayrollPaymentTransitionCommand = Readonly<{
  id: PayrollPaymentBatchId;
  version: number;
  transition: PayrollPaymentTransition;
  reason: string;
}>;
