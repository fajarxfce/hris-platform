import type { ExpenseReportClaimPageDto, ExpenseReportDto } from "../models/expense-report-dto";

export interface ExpenseReportDataSource {
  claims(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpenseReportClaimPageDto>;
  load(
    company: string,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<ExpenseReportDto>;
}
