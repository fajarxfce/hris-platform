export type PayrollPaymentOutcomeDto = Readonly<{
  itemId: string;
  status: "SUCCEEDED" | "FAILED";
  transactionReference: string | null;
  occurredAt: string;
  reason: string;
  confirmedNoTransfer: boolean;
}>;
export type PayrollPaymentReconciliationDto = Readonly<{
  expectedVersion: number;
  results: readonly PayrollPaymentOutcomeDto[];
  reason: string;
}>;
