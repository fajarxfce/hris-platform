import type { CompanyId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type ReportKind, reportDefinitions } from "../../domain/entities/report-catalogue";
import type {
  ReportFilterDefinition,
  ReportFilterParameters,
  SavedReportFilter,
} from "../../domain/entities/saved-report-filter";
import { canReadReportKind } from "../../domain/policies/report-catalogue-policy";
import { normalizeReportFilter } from "../../domain/policies/saved-report-filter-policy";
import { reportPaths } from "./report-catalogue-view";

export const savedReportFiltersPath = (company: CompanyId) =>
  `/reports/saved-filters?${new URLSearchParams({ company })}`;
export const savedReportFilterPath = (company: CompanyId, id: string) =>
  `/reports/saved-filters/${encodeURIComponent(id)}?${new URLSearchParams({ company })}`;
export function reportFilterParameters(
  company: CompanyId,
  p: ReportFilterParameters,
): URLSearchParams {
  const query = new URLSearchParams({ company });
  for (const key of ["asOf", "from", "until", "status"] as const)
    if (p[key] !== null) query.set(key, p[key]);
  if (p.companies.length) query.set("companies", p.companies.join(","));
  return query;
}
export function openSavedReportFilter(filter: SavedReportFilter): string {
  return `${reportPaths[filter.kind]}?${reportFilterParameters(filter.companyId, filter.parameters)}`;
}
export function saveAppliedReportFilter(
  company: CompanyId,
  kind: ReportKind,
  parameters: ReportFilterParameters,
): string | null {
  const valid = normalizeReportFilter(company, {
    name: "Selection",
    kind,
    definitionVersion: reportDefinitions[kind],
    parameters,
  });
  if (!valid.ok) return null;
  const query = reportFilterParameters(company, valid.value.parameters);
  query.set("kind", kind);
  return `/reports/saved-filters/new?${query}`;
}
export function reportFilterSeed(
  access: CompanyAccess,
  today: string,
  query: URLSearchParams,
): ReportFilterDefinition | null {
  if (
    query.toString().length > 2500 ||
    [...query.keys()].some((key) => query.getAll(key).length !== 1)
  )
    return null;
  const kind =
    query.get("kind") ??
    (Object.keys(reportDefinitions) as ReportKind[]).find((kind) =>
      canReadReportKind(access.permissions, kind),
    ) ??
    "HEADCOUNT";
  if (!Object.hasOwn(reportDefinitions, kind)) return null;
  const month = kind === "ATTENDANCE_CLOSINGS" || kind === "PAYROLL_COSTS";
  const date = month ? today.slice(0, 7) : today;
  const parameters: ReportFilterParameters = {
    asOf: query.get("asOf") ?? (kind === "HEADCOUNT" ? today : null),
    from: query.get("from") ?? (kind === "HEADCOUNT" ? null : date),
    until: query.get("until") ?? (kind === "HEADCOUNT" ? null : date),
    status: query.get("status"),
    companies: (query.has("companies")
      ? query.get("companies")?.split(",", 34)
      : kind === "HEADCOUNT"
        ? [access.companyId]
        : []) as CompanyId[],
  };
  const valid = normalizeReportFilter(access.companyId, {
    name: "Selection",
    kind: kind as ReportKind,
    definitionVersion: reportDefinitions[kind as ReportKind],
    parameters,
  });
  return valid.ok ? { ...valid.value, name: "" } : null;
}
