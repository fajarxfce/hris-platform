import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimId, ExpenseClaimStatus } from "./expense-claim";
export type ExpenseClaimSummary = Readonly<{
  id: ExpenseClaimId;
  companyId: CompanyId;
  employmentId: string;
  employeeNumber: string;
  employeeName: string;
  title: string;
  totalAmount: string;
  createdAt: string;
  status: ExpenseClaimStatus;
  version: number;
}>;
export type ExpenseClaimPage = Readonly<{
  items: readonly ExpenseClaimSummary[];
  nextCursor: string | null;
}>;
