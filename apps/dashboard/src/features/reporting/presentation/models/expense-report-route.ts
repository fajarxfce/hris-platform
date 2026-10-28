import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimStatus } from "../../../expenses/domain/entities/expense-claim";
import type { ExpenseReportQuery } from "../../domain/entities/expense-report-claim";

export function expenseReportQuery(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): ExpenseReportQuery {
  const empty = { from: `${today.slice(0, 4)}-01-01`, until: today, status: null, after: null };
  if (parameters.has("company") && parameters.get("company") !== company) return empty;
  return {
    from: parameters.get("from") ?? empty.from,
    until: parameters.get("until") ?? today,
    status: parameters.get("status") as ExpenseClaimStatus | null,
    after: parameters.get("after"),
  };
}

export function expenseReportParameters(
  company: CompanyId,
  query: ExpenseReportQuery,
): URLSearchParams {
  const result = new URLSearchParams({ company, from: query.from, until: query.until });
  if (query.status !== null) result.set("status", query.status);
  if (query.after !== null) result.set("after", query.after);
  return result;
}
