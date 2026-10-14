import type { PayrollPaymentBatchId } from "./payroll-payment-batch";
import type { PayrollPaymentDestination } from "./payroll-payment-destination";
export type PayrollPaymentPreparationCommand = Readonly<{
  id: PayrollPaymentBatchId;
  title: string;
  reason: string;
  items: readonly Readonly<{
    id: string;
    assessmentId: string;
    destination: PayrollPaymentDestination;
  }>[];
}>;
