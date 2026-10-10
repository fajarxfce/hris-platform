import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseSearch } from "../../domain/entities/lifecycle-case-search";

export function lifecycleCaseSearch(
  parameters: URLSearchParams,
  company: CompanyId,
): LifecycleCaseSearch {
  if (parameters.has("company") && parameters.get("company") !== company)
    return { status: "OPEN", employmentId: null, after: null };
  return {
    status: parameters.get("status") ?? "OPEN",
    employmentId: parameters.get("employmentId"),
    after: parameters.get("after"),
  };
}
export function lifecycleCaseParameters(
  company: CompanyId,
  search: LifecycleCaseSearch,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, status: search.status });
  if (search.employmentId !== null) parameters.set("employmentId", search.employmentId);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
