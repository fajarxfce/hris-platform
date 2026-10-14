import type { CompanyId } from "../../../../core/domain/identifiers";
export function compensationEmployeeParameters(
  company: CompanyId,
  query = "",
  after: string | null = null,
) {
  const parameters = new URLSearchParams({ company });
  if (query) parameters.set("query", query);
  if (after !== null) parameters.set("after", after);
  return parameters;
}
export function compensationEmployeeQuery(parameters: URLSearchParams, company: CompanyId) {
  const replaced = parameters.has("company") && parameters.get("company") !== company;
  return {
    query: replaced ? "" : (parameters.get("query") ?? ""),
    after: replaced ? null : parameters.get("after"),
  };
}
export function compensationReviewParameters(
  company: CompanyId,
  month: string,
  after: string | null = null,
) {
  const parameters = new URLSearchParams({ company, month });
  if (after !== null) parameters.set("historyAfter", after);
  return parameters;
}
