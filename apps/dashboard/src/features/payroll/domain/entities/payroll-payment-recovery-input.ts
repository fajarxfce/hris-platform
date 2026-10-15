export type PayrollPaymentRecoveryInput = Readonly<{
  amount: string;
  transactionReference: string;
  occurredAt: string;
  reason: string;
}>;
