import type { ExpensePaymentResult } from "./expense-payment-reconciliation";
export type ExpensePaymentOutcome = ExpensePaymentResult &
  Readonly<{ confirmedNoTransfer: boolean }>;
