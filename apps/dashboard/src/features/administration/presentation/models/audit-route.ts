import { type AuditSearch, defaultAuditSearch } from "../../domain/entities/audit-search";

export function auditSearchFromParameters(
  parameters: URLSearchParams,
  companyId: string,
): AuditSearch {
  return Object.freeze({
    from: parameters.get("from"),
    until: parameters.get("until"),
    cursor:
      parameters.has("company") && parameters.get("company") !== companyId
        ? null
        : parameters.get("cursor"),
    actorId: parameters.get("actorId"),
    resourceType: parameters.get("resourceType"),
    resourceId: parameters.get("resourceId"),
    action: parameters.get("action"),
  });
}

export function auditSearchParameters(query: AuditSearch, companyId: string): URLSearchParams {
  const parameters = new URLSearchParams({ company: companyId });
  for (const key of Object.keys(defaultAuditSearch) as (keyof AuditSearch)[]) {
    if (query[key] !== null) parameters.set(key, query[key]);
  }
  return parameters;
}
