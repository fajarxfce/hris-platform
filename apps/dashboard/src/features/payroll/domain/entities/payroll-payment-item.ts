import type { PayrollPaymentDestination } from "./payroll-payment-destination";
import type { payrollPaymentStatuses } from "./payroll-payment-status";
export type PayrollPaymentItem = Readonly<{
  id: string;
  assessmentId: string;
  employmentId: string;
  employeeNumber: string;
  employeeName: string;
  taxMonth: string;
  plannedPaymentDate: string;
  amount: string;
  currency: "IDR";
  destination: PayrollPaymentDestination;
  status: (typeof payrollPaymentStatuses)[number];
  version: number;
  batchVersion: number;
  transactionReference: string | null;
  occurredAt: string | null;
}>;
