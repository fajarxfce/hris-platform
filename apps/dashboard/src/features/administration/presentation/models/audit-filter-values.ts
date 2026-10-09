import { type AuditSearch, defaultAuditSearch } from "../../domain/entities/audit-search";
import { auditInstantMicros } from "../../domain/policies/audit-time";

export type AuditFilterValues = {
  from: string;
  until: string;
  actorId: string;
  resourceType: string;
  resourceId: string;
  action: string;
};

/** Native datetime inputs show UTC wall time explicitly; browser timezone never shifts the filter. */
export function auditFiltersFromSearch(query: AuditSearch): AuditFilterValues {
  return {
    from:
      query.from !== null && auditInstantMicros(query.from) !== null ? query.from.slice(0, 19) : "",
    until:
      query.until !== null && auditInstantMicros(query.until) !== null
        ? query.until.slice(0, 19)
        : "",
    actorId: query.actorId ?? "",
    resourceType: query.resourceType ?? "",
    resourceId: query.resourceId ?? "",
    action: query.action ?? "",
  };
}

export function auditSearchFromFilters(values: AuditFilterValues): AuditSearch {
  return {
    ...defaultAuditSearch,
    from: values.from === "" ? null : `${values.from}${values.from.length === 16 ? ":00" : ""}Z`,
    until:
      values.until === "" ? null : `${values.until}${values.until.length === 16 ? ":00" : ""}Z`,
    actorId: values.actorId.trim() || null,
    resourceType: values.resourceType.trim() || null,
    resourceId: values.resourceId.trim() || null,
    action: values.action.trim() || null,
  };
}
