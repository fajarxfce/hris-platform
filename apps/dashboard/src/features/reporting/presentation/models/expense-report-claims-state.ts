import type { Failure } from "../../../../core/domain/result";
import type { ExpenseReportClaimPage } from "../../domain/entities/expense-report-claim";

export type ExpenseReportClaimsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: ExpenseReportClaimPage | null;
  failure: Failure | null;
}>;
export const initialExpenseReportClaimsState: ExpenseReportClaimsState = {
  stage: "loading",
  page: null,
  failure: null,
};
