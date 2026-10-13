import type { ExpensePaymentBatchId } from "./expense-payment-batch";
import type { ExpensePaymentDestination } from "./expense-payment-destination";
export type ExpensePaymentPreparationCommand = Readonly<{
  id: ExpensePaymentBatchId;
  title: string;
  reason: string;
  items: readonly Readonly<{
    id: string;
    submissionId: string;
    destination: ExpensePaymentDestination;
  }>[];
}>;
