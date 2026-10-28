import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseReportQuery } from "../../../reporting/domain/entities/expense-report-claim";
import {
  isExpenseReportPage,
  isExpenseReportRange,
} from "../../../reporting/domain/policies/expense-report-policy";
import type { ExpenseClaimStatus } from "../../domain/entities/expense-claim";

/** Only a validated same-company report selection can become return context. */
export function expenseReportOrigin(
  parameters: URLSearchParams,
  company: CompanyId,
): ExpenseReportQuery | null {
  if (parameters.get("company") !== company) return null;
  const from = parameters.get("reportFrom"),
    until = parameters.get("reportUntil");
  if (!from || !until || !isExpenseReportRange(from, until)) return null;
  const query = {
    from,
    until,
    status: parameters.get("reportStatus") as ExpenseClaimStatus | null,
    after: parameters.get("reportAfter"),
  };
  return isExpenseReportPage(company, query) ? Object.freeze(query) : null;
}

export function expenseReportReturn(company: CompanyId, origin: ExpenseReportQuery): string {
  const query = new URLSearchParams({ company, from: origin.from, until: origin.until });
  if (origin.status !== null) query.set("status", origin.status);
  if (origin.after !== null) query.set("after", origin.after);
  return `/reports/expenses/claims?${query}`;
}
