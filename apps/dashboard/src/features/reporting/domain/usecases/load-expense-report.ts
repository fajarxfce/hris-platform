import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadExpenseReport, isExpenseReportRange } from "../policies/expense-report-policy";
import type { ExpenseReportRepository } from "../repositories/expense-report-repository";

export class LoadExpenseReport {
  constructor(private readonly reports: ExpenseReportRepository) {}

  execute(access: CompanyAccess, from: string, until: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpenseReport(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isExpenseReportRange(from, until))
      return Promise.resolve({
        ok: false as const,
        failure: { code: "invalid_report_range", fields: {}, parameters: { maximumDays: "366" } },
      });
    return this.reports.load(access.companyId, from, until, signal);
  }
}
