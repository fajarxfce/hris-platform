import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseReportQuery } from "../entities/expense-report-claim";
import {
  canReadExpenseReport,
  isExpenseReportPage,
  isExpenseReportRange,
} from "../policies/expense-report-policy";
import type { ExpenseReportRepository } from "../repositories/expense-report-repository";

export class LoadExpenseReportClaims {
  constructor(private readonly reports: ExpenseReportRepository) {}
  execute(access: CompanyAccess, query: ExpenseReportQuery, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpenseReport(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isExpenseReportRange(query.from, query.until))
      return Promise.resolve({
        ok: false as const,
        failure: { code: "invalid_report_range", fields: {}, parameters: { maximumDays: "366" } },
      });
    if (!isExpenseReportPage(access.companyId, query))
      return Promise.resolve(failed("invalid_page"));
    return this.reports.claims(access.companyId, Object.freeze({ ...query }), signal);
  }
}
