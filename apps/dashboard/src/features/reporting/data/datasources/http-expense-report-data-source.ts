import type { HttpClient } from "../../../../core/data/http/http-client";
import { expenseReportClaimPageDto, expenseReportDto } from "../models/expense-report-dto";
import type { ExpenseReportDataSource } from "./expense-report-data-source";

export class HttpExpenseReportDataSource implements ExpenseReportDataSource {
  constructor(private readonly http: HttpClient) {}

  async load(company: string, from: string, until: string, signal: AbortSignal) {
    const query = new URLSearchParams({ from, until });
    return expenseReportDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/expenses?${query}`,
        },
        signal,
      ),
    );
  }

  async claims(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ from, until, limit: "50" });
    if (status !== null) query.set("status", status);
    if (after !== null) query.set("after", after);
    return expenseReportClaimPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/expenses/claims?${query}`,
        },
        signal,
      ),
    );
  }
}
