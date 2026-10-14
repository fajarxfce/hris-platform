import type { CompanyId } from "../../../../core/domain/identifiers";
import {
  type TaxOpeningSearch,
  taxOpeningEmployeeStatuses,
} from "../../domain/entities/tax-opening-review";
export function taxOpeningSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  year: string,
): TaxOpeningSearch {
  const replaced = parameters.has("company") && parameters.get("company") !== company;
  return {
    year: replaced ? year : (parameters.get("year") ?? year),
    query: replaced ? "" : (parameters.get("query") ?? ""),
    status: replaced
      ? null
      : (taxOpeningEmployeeStatuses.find((status) => status === parameters.get("status")) ?? null),
    after: replaced ? null : parameters.get("after"),
  };
}
export function taxOpeningParameters(
  company: CompanyId,
  search: TaxOpeningSearch,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, year: search.year });
  if (search.query) parameters.set("query", search.query);
  if (search.status !== null) parameters.set("status", search.status);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
export function taxOpeningReviewParameters(
  company: CompanyId,
  year: string,
  after: string | null = null,
  tab = "overview",
): URLSearchParams {
  const parameters = new URLSearchParams({ company, year });
  if (after !== null) parameters.set("historyAfter", after);
  if (tab === "history") parameters.set("tab", tab);
  return parameters;
}
