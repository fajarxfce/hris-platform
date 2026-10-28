import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseReportQuery } from "../../domain/entities/expense-report-claim";
import type { ExpenseReportRepository } from "../../domain/repositories/expense-report-repository";
import type { ExpenseReportDataSource } from "../datasources/expense-report-data-source";
import { toExpenseReportClaimPage } from "../mappers/expense-report-claim-mapper";
import { toExpenseReport } from "../mappers/expense-report-mapper";

export class RemoteExpenseReportRepository implements ExpenseReportRepository {
  constructor(private readonly source: ExpenseReportDataSource) {}

  claims(company: CompanyId, query: ExpenseReportQuery, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseReportClaimPage(
        await this.source.claims(
          company,
          query.from,
          query.until,
          query.status,
          query.after,
          signal,
        ),
        company,
        query,
      ),
    );
  }

  load(company: CompanyId, from: string, until: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseReport(await this.source.load(company, from, until, signal), company, from, until),
    );
  }
}
