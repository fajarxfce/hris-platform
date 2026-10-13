export const expensePaymentStatuses = [
  "PREPARED",
  "PENDING",
  "SUCCEEDED",
  "FAILED",
  "CANCELLED",
] as const;
export type ExpensePaymentProgress = Readonly<{
  batchId: string;
  itemId: string;
  status: (typeof expensePaymentStatuses)[number];
  createdAt: string;
  releasedAt: string | null;
  settledAt: string | null;
}>;
