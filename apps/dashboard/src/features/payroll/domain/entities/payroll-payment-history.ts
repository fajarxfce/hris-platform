import type { PayrollPaymentBatchStatus } from "./payroll-payment-batch";
export const payrollPaymentActionKinds = [
  "PREPARED",
  "RELEASED",
  "CANCELLED",
  "RECONCILED",
] as const;
export type PayrollPaymentAction = Readonly<{
  version: number;
  kind: (typeof payrollPaymentActionKinds)[number];
  status: PayrollPaymentBatchStatus;
  actorId: string;
  reason: string;
  recordedAt: string;
}>;
export type PayrollPaymentHistory = Readonly<{
  items: readonly PayrollPaymentAction[];
  nextCursor: string | null;
}>;
