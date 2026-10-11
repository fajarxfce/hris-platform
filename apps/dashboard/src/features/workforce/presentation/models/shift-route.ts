import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ShiftSearch } from "../../domain/entities/shift-search";

export const shiftsPath = "/workforce/shifts";
export function shiftSearch(parameters: URLSearchParams, company: CompanyId): ShiftSearch {
  if (parameters.has("company") && parameters.get("company") !== company)
    return Object.freeze({ query: "", after: null });
  return Object.freeze({ query: parameters.get("query") ?? "", after: parameters.get("after") });
}
export function shiftParameters(
  company: CompanyId,
  search: ShiftSearch = { query: "", after: null },
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (search.query !== "") parameters.set("query", search.query);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
