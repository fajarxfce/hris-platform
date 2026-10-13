import type { ExpensePaymentBatchId } from "./expense-payment-batch";
import type { ExpensePaymentTransition } from "./expense-payment-transition";
export type ExpensePaymentTransitionCommand = Readonly<{
  id: ExpensePaymentBatchId;
  version: number;
  transition: ExpensePaymentTransition;
  reason: string;
}>;
