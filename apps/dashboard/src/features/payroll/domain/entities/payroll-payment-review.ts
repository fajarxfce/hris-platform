import type { PayrollPaymentBatch } from "./payroll-payment-batch";
import type { PayrollPaymentHistory } from "./payroll-payment-history";
import type { PayrollPaymentReconciliation } from "./payroll-payment-reconciliation";
export const payrollPaymentAvailableActions = ["RELEASE", "CANCEL", "RECONCILE", "EXPORT"] as const;
export type PayrollPaymentAvailableAction = (typeof payrollPaymentAvailableActions)[number];
export type PayrollPaymentReview = Readonly<{
  batch: PayrollPaymentBatch;
  history: PayrollPaymentHistory;
  results: readonly PayrollPaymentReconciliation[];
  availableActions: readonly PayrollPaymentAvailableAction[];
}>;
