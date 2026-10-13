import type { ExpensePaymentBatch } from "./expense-payment-batch";
export type ExpensePaymentSummary = Omit<
  ExpensePaymentBatch,
  "releasedBy" | "releasedAt" | "items"
> &
  Readonly<{ pendingCount: number; succeededCount: number; failedCount: number }>;
export type ExpensePaymentPage = Readonly<{
  items: readonly ExpensePaymentSummary[];
  nextCursor: string | null;
}>;
