import { Temporal } from "@js-temporal/polyfill";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ReportKind } from "../../domain/entities/report-catalogue";
import type { SavedReportFilter } from "../../domain/entities/saved-report-filter";
import { normalizeReportFilter } from "../../domain/policies/saved-report-filter-policy";
import type {
  ReportFilterReceiptDto,
  SavedReportFilterDto,
  SavedReportFiltersDto,
} from "../models/saved-report-filter-dto";

export function toSavedReportFilter(
  raw: SavedReportFilterDto,
  company: CompanyId,
): SavedReportFilter {
  const canonical = normalizeReportFilter(company, {
    name: raw.name,
    kind: raw.kind as ReportKind,
    definitionVersion: raw.definitionVersion,
    parameters: { ...raw.parameters, companies: raw.parameters.companies as CompanyId[] },
  });
  if (
    raw.companyId.toLowerCase() !== company ||
    !canonical.ok ||
    canonical.value.name !== raw.name ||
    Temporal.Instant.compare(raw.updatedAt, raw.createdAt) < 0 ||
    (raw.kind === "HEADCOUNT" && raw.parameters.companies.length === 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...canonical.value,
    id: raw.id.toLowerCase(),
    companyId: company,
    version: raw.version,
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  });
}
export function toSavedReportFilters(
  raw: SavedReportFiltersDto,
  company: CompanyId,
): readonly SavedReportFilter[] {
  if (raw.companyId.toLowerCase() !== company) throw new InvalidHttpResponseError();
  const items = raw.items.map((item) => toSavedReportFilter(item, company));
  if (new Set(items.map((item) => item.id)).size !== items.length)
    throw new InvalidHttpResponseError();
  return Object.freeze(items);
}
export function toReportFilterReceipt(
  raw: ReportFilterReceiptDto,
  id: string,
  expectedVersion: number | null,
  deleting: boolean,
) {
  const nextVersion = (expectedVersion ?? -1) + 1;
  if (
    raw.id.toLowerCase() !== id ||
    (raw.version !== nextVersion &&
      !(
        deleting &&
        expectedVersion !== null &&
        expectedVersion > 0 &&
        raw.version === expectedVersion
      )) ||
    (!deleting && raw.version > 9999)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id, version: raw.version });
}
