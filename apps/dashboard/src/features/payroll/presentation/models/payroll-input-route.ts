import type { CompanyId } from "../../../../core/domain/identifiers";
import {
  type PayrollInputSearch,
  payrollInputEmployeeStatuses,
} from "../../domain/entities/payroll-input-review";
export function payrollInputSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  month: string,
): PayrollInputSearch {
  const replaced = parameters.has("company") && parameters.get("company") !== company;
  return {
    month: replaced ? month : (parameters.get("month") ?? month),
    query: replaced ? "" : (parameters.get("query") ?? ""),
    status: replaced
      ? null
      : (payrollInputEmployeeStatuses.find((status) => status === parameters.get("status")) ??
        null),
    after: replaced ? null : parameters.get("after"),
  };
}
export function payrollInputParameters(
  company: CompanyId,
  search: PayrollInputSearch,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, month: search.month });
  if (search.query) parameters.set("query", search.query);
  if (search.status !== null) parameters.set("status", search.status);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
export function payrollInputReviewParameters(
  company: CompanyId,
  month: string,
  after: string | null = null,
  tab = "overview",
): URLSearchParams {
  const parameters = new URLSearchParams({ company, month });
  if (after !== null) parameters.set("historyAfter", after);
  if (tab === "history") parameters.set("tab", tab);
  return parameters;
}
