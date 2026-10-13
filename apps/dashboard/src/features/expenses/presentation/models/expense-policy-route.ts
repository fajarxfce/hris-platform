import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpensePolicyQuery } from "../../domain/entities/expense-policy-definition";

export function expensePolicyQuery(
  parameters: URLSearchParams,
  company: CompanyId,
): ExpensePolicyQuery {
  if (parameters.has("company") && parameters.get("company") !== company)
    return { active: null, after: null };
  return { active: parameters.get("active"), after: parameters.get("after") };
}
export function expensePolicyParameters(
  company: CompanyId,
  query: ExpensePolicyQuery,
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (query.active !== null) parameters.set("active", query.active);
  if (query.after !== null) parameters.set("after", query.after);
  return parameters;
}
