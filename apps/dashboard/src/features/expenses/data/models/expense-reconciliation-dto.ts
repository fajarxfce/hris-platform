export type ExpensePaymentOutcomeDto = Readonly<{
  itemId: string;
  status: "SUCCEEDED" | "FAILED";
  transactionReference: string | null;
  occurredAt: string;
  reason: string;
  confirmedNoTransfer: boolean;
}>;
export type ExpenseReconciliationDto = Readonly<{
  expectedVersion: number;
  results: readonly ExpensePaymentOutcomeDto[];
  reason: string;
}>;
