import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ExpenseReport } from "../entities/expense-report";
import type { ExpenseReportClaimPage, ExpenseReportQuery } from "../entities/expense-report-claim";

export interface ExpenseReportRepository {
  claims(
    company: CompanyId,
    query: ExpenseReportQuery,
    signal: AbortSignal,
  ): Promise<Result<ExpenseReportClaimPage>>;
  load(
    company: CompanyId,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<Result<ExpenseReport>>;
}
