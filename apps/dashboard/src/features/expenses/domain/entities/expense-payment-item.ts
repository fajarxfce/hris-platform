import type { ExpensePaymentDestination } from "./expense-payment-destination";
import type { expensePaymentStatuses } from "./expense-payment-progress";
export type ExpensePaymentItem = Readonly<{
  id: string;
  claimId: string;
  submissionId: string;
  employmentId: string;
  employeeNumber: string;
  employeeName: string;
  amount: string;
  currency: "IDR";
  destination: ExpensePaymentDestination;
  status: (typeof expensePaymentStatuses)[number];
  version: number;
  batchVersion: number;
  transactionReference: string | null;
  occurredAt: string | null;
}>;
