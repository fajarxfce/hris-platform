import type { CompanyId } from "../../../../core/domain/identifiers";

export function lifecycleAfter(parameters: URLSearchParams, company: CompanyId): string | null {
  if (parameters.has("company") && parameters.get("company") !== company) return null;
  return parameters.get("after");
}
export function lifecycleParameters(company: CompanyId, after: string | null): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (after !== null) parameters.set("after", after);
  return parameters;
}
