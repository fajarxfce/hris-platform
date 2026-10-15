import type { PayrollPaymentBatchId } from "./payroll-payment-batch";
import type { PayrollPaymentDestination } from "./payroll-payment-destination";
export type PayrollPaymentPreparationCommand = Readonly<{
  id: PayrollPaymentBatchId;
  title: string;
  reason: string;
  items: readonly Readonly<{
    id: string;
    assessmentId: string;
    assessmentRevision: number;
    paymentVersion: number;
    amount: string;
    destination: PayrollPaymentDestination;
  }>[];
}>;
