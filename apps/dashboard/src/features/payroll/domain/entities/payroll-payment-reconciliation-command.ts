import type { PayrollPaymentBatchId } from "./payroll-payment-batch";
import type { PayrollPaymentOutcome } from "./payroll-payment-outcome";
export type PayrollPaymentReconciliationCommand = Readonly<{
  id: PayrollPaymentBatchId;
  version: number;
  results: readonly PayrollPaymentOutcome[];
  reason: string;
}>;
