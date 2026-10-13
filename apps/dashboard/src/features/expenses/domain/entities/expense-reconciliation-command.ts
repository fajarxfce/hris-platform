import type { ExpensePaymentBatchId } from "./expense-payment-batch";
import type { ExpensePaymentOutcome } from "./expense-payment-outcome";
export type ExpenseReconciliationCommand = Readonly<{
  id: ExpensePaymentBatchId;
  version: number;
  results: readonly ExpensePaymentOutcome[];
  reason: string;
}>;
