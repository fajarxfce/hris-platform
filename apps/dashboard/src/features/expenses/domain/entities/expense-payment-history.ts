import type { ExpensePaymentBatchStatus } from "./expense-payment-batch";
export const expensePaymentActionKinds = [
  "PREPARED",
  "RELEASED",
  "CANCELLED",
  "RECONCILED",
] as const;
export type ExpensePaymentAction = Readonly<{
  version: number;
  kind: (typeof expensePaymentActionKinds)[number];
  status: ExpensePaymentBatchStatus;
  actorId: string;
  reason: string;
  recordedAt: string;
}>;
export type ExpensePaymentHistory = Readonly<{
  items: readonly ExpensePaymentAction[];
  nextCursor: string | null;
}>;
