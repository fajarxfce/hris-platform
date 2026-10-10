import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OrganizationUnitSearchInput } from "../../domain/entities/organization-unit-search";

export function organizationSearchFromParameters(
  parameters: URLSearchParams,
  company: CompanyId,
): OrganizationUnitSearchInput {
  if (parameters.has("company") && parameters.get("company") !== company)
    return Object.freeze({ query: "", kind: null, active: null, after: null });
  return Object.freeze({
    query: parameters.get("query") ?? "",
    kind: parameters.get("kind"),
    active: parameters.get("active"),
    after: parameters.get("after"),
  });
}

export function organizationSearchParameters(
  search: OrganizationUnitSearchInput,
  company: CompanyId,
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (search.query !== "") parameters.set("query", search.query);
  if (search.kind !== null) parameters.set("kind", search.kind);
  if (search.active !== null) parameters.set("active", search.active);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
