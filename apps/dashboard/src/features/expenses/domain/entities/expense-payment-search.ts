import type { ExpensePaymentBatchStatus } from "./expense-payment-batch";
export type ExpensePaymentSearchInput = Readonly<{
  from: string;
  until: string;
  status: string | null;
  after: string | null;
}>;
export type ExpensePaymentSearch = Omit<ExpensePaymentSearchInput, "status"> &
  Readonly<{ status: ExpensePaymentBatchStatus | null }>;
