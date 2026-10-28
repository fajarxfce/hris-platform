import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimStatus } from "../../../expenses/domain/entities/expense-claim";

export type ExpenseReportQuery = Readonly<{
  from: string;
  until: string;
  status: ExpenseClaimStatus | null;
  after: string | null;
}>;
export type ExpenseReportClaim = Readonly<{
  id: string;
  submissionId: string;
  submissionNumber: number;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  title: string;
  status: ExpenseClaimStatus;
  submittedAt: string;
  version: number;
  lines: number;
  amount: string;
  submissionAmount: string;
}>;
export type ExpenseReportClaimPage = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  status: ExpenseClaimStatus | null;
  evaluatedAt: string;
  definitionVersion: "expenses.v1";
  currency: "IDR";
  items: readonly ExpenseReportClaim[];
  nextCursor: string | null;
}>;
