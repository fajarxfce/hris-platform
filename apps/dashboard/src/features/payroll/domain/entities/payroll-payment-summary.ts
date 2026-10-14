import type { PayrollPaymentBatch } from "./payroll-payment-batch";
export type PayrollPaymentSummary = Omit<
  PayrollPaymentBatch,
  "releasedBy" | "releasedAt" | "items"
> &
  Readonly<{ pendingCount: number; succeededCount: number; failedCount: number }>;
export type PayrollPaymentPage = Readonly<{
  items: readonly PayrollPaymentSummary[];
  nextCursor: string | null;
}>;
