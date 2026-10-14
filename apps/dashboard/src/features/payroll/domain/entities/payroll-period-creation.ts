export type PayrollPeriodCreation = Readonly<{
  id: string;
  earningsMonth: string;
  plannedPaymentDate: string;
  employeeIds: readonly string[];
  reason: string;
}>;
export type PayrollPeriodFields = Pick<
  PayrollPeriodCreation,
  "earningsMonth" | "plannedPaymentDate" | "reason"
>;
