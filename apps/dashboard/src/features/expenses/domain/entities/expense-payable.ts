import type { CompanyId } from "../../../../core/domain/identifiers";
export const expensePayableActions = ["PREPARE"] as const;
export type ExpensePayable = Readonly<{
  companyId: CompanyId;
  claimId: string;
  submissionId: string;
  employmentId: string;
  employeeNumber: string;
  employeeName: string;
  title: string;
  amount: string;
  currency: "IDR";
  approvedAt: string;
  availableActions: readonly (typeof expensePayableActions)[number][];
}>;
export type ExpensePayablePage = Readonly<{
  items: readonly ExpensePayable[];
  nextCursor: string | null;
}>;
