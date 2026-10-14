import type { PayrollPaymentBatchStatus } from "./payroll-payment-batch";
export type PayrollPaymentSearchInput = Readonly<{
  from: string;
  until: string;
  status: string | null;
  after: string | null;
}>;
export type PayrollPaymentSearch = Omit<PayrollPaymentSearchInput, "status"> &
  Readonly<{ status: PayrollPaymentBatchStatus | null }>;
