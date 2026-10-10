import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeSearch } from "../../domain/entities/employee-search";

export function employeeSearchFromParameters(
  parameters: URLSearchParams,
  companyId: CompanyId,
  today: string,
): EmployeeSearch {
  if (parameters.has("company") && parameters.get("company") !== companyId)
    return Object.freeze({ asOf: today, query: "", after: null });
  return Object.freeze({
    asOf: parameters.get("asOf") ?? today,
    query: parameters.get("query") ?? "",
    after: parameters.get("after"),
  });
}

export function employeeSearchParameters(
  search: EmployeeSearch,
  companyId: CompanyId,
): URLSearchParams {
  const parameters = new URLSearchParams({ company: companyId, asOf: search.asOf });
  if (search.query !== "") parameters.set("query", search.query);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}

export type EmployeeTab = "overview" | "history";
