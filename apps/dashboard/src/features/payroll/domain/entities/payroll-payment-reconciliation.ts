export type PayrollPaymentResult = Readonly<{
  itemId: string;
  status: "SUCCEEDED" | "FAILED";
  transactionReference: string | null;
  occurredAt: string;
  reason: string;
}>;
export type PayrollPaymentReconciliation = Readonly<{
  batchVersion: number;
  actorId: string;
  recordedAt: string;
  resultingStatus: "RELEASED" | "CLOSED";
  results: readonly PayrollPaymentResult[];
}>;
