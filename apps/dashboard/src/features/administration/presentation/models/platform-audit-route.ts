import { type AuditSearch, defaultAuditSearch } from "../../domain/entities/audit-search";

export function platformAuditSearchFromParameters(parameters: URLSearchParams): AuditSearch {
  const result = { ...defaultAuditSearch };
  for (const key of Object.keys(defaultAuditSearch) as (keyof AuditSearch)[])
    result[key] = parameters.get(key);
  if (parameters.get("scope") !== "platform" || parameters.has("company")) result.cursor = null;
  return Object.freeze(result);
}

export function platformAuditSearchParameters(query: AuditSearch): URLSearchParams {
  const parameters = new URLSearchParams({ scope: "platform" });
  for (const key of Object.keys(defaultAuditSearch) as (keyof AuditSearch)[]) {
    if (query[key] !== null) parameters.set(key, query[key]);
  }
  return parameters;
}
