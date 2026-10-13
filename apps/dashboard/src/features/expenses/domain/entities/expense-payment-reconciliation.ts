export type ExpensePaymentResult = Readonly<{
  itemId: string;
  status: "SUCCEEDED" | "FAILED";
  transactionReference: string | null;
  occurredAt: string;
  reason: string;
}>;
export type ExpensePaymentReconciliation = Readonly<{
  batchVersion: number;
  actorId: string;
  recordedAt: string;
  resultingStatus: "RELEASED" | "CLOSED";
  results: readonly ExpensePaymentResult[];
}>;
