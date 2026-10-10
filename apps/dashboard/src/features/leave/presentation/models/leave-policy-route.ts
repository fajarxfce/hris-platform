import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeavePolicyQuery } from "../../domain/entities/leave-policy-definition";

export function leavePolicyQuery(
  parameters: URLSearchParams,
  company: CompanyId,
): LeavePolicyQuery {
  if (parameters.has("company") && parameters.get("company") !== company)
    return { active: null, after: null };
  return { active: parameters.get("active"), after: parameters.get("after") };
}
export function leavePolicyParameters(
  company: CompanyId,
  query: LeavePolicyQuery,
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (query.active !== null) parameters.set("active", query.active);
  if (query.after !== null) parameters.set("after", query.after);
  return parameters;
}
