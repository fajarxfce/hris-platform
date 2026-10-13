import type { ExpensePaymentBatch } from "./expense-payment-batch";
import type { ExpensePaymentHistory } from "./expense-payment-history";
import type { ExpensePaymentReconciliation } from "./expense-payment-reconciliation";
export const expensePaymentAvailableActions = ["RELEASE", "CANCEL", "RECONCILE", "EXPORT"] as const;
export type ExpensePaymentAvailableAction = (typeof expensePaymentAvailableActions)[number];
export type ExpensePaymentReview = Readonly<{
  batch: ExpensePaymentBatch;
  history: ExpensePaymentHistory;
  results: readonly ExpensePaymentReconciliation[];
  availableActions: readonly ExpensePaymentAvailableAction[];
}>;
