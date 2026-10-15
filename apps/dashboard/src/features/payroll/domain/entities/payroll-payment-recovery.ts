export type PayrollPaymentRecovery = Readonly<{
  id: string;
  assessmentId: string;
  assessmentRevision: number;
  paymentVersion: number;
  amount: string;
  currency: "IDR";
  transactionReference: string;
  occurredAt: string;
  recordedAt: string;
  actorId: string;
  reason: string;
}>;
