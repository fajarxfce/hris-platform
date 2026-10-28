import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimStatus } from "../../../expenses/domain/entities/expense-claim";

export type ExpenseReportCounts = Readonly<{
  claims: number;
  employments: number;
  lines: number;
  amount: string;
}>;
export type ExpenseReport = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  evaluatedAt: string;
  currency: "IDR";
  definitionVersion: "expenses.v1";
  totals: ExpenseReportCounts;
  statuses: readonly Readonly<{ status: ExpenseClaimStatus; counts: ExpenseReportCounts }>[];
}>;
