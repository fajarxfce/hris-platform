import type { CompanyId } from "../../../../core/domain/identifiers";
import { payrollPeriodStatuses } from "../../domain/entities/payroll-period";
import type { PayrollPeriodSearch } from "../../domain/entities/payroll-period-search";
export function payrollPeriodSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): PayrollPeriodSearch {
  const replaced = parameters.has("company") && parameters.get("company") !== company;
  const from = replaced
      ? `${today.slice(0, 4)}-01`
      : (parameters.get("from") ?? `${today.slice(0, 4)}-01`),
    until = replaced
      ? `${today.slice(0, 4)}-12`
      : (parameters.get("until") ?? `${today.slice(0, 4)}-12`);
  const status = replaced
    ? null
    : (payrollPeriodStatuses.find((status) => status === parameters.get("status")) ?? null);
  return { from, until, status, after: replaced ? null : parameters.get("after") };
}
export function payrollPeriodParameters(
  company: CompanyId,
  search: PayrollPeriodSearch,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, from: search.from, until: search.until });
  if (search.status !== null) parameters.set("status", search.status);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
export function payrollPeriodDetailsParameters(
  company: CompanyId,
  memberAfter: string | null = null,
  historyAfter: string | null = null,
  tab = "participants",
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (memberAfter !== null) parameters.set("memberAfter", memberAfter);
  if (historyAfter !== null) parameters.set("historyAfter", historyAfter);
  if (tab === "history") parameters.set("tab", tab);
  return parameters;
}
