import { Temporal } from "@js-temporal/polyfill";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ReportKind } from "../../domain/entities/report-catalogue";
import type {
  ReportExportCursor,
  ReportExportDetails,
  ReportExportHistory,
} from "../../domain/entities/report-export";
import {
  isReportExportCursor,
  reportExportPageSize,
  supportsReportDetail,
} from "../../domain/policies/report-export-policy";
import { normalizeReportSelection } from "../../domain/policies/report-selection-policy";
import type { ReportExportDto, ReportExportHistoryDto } from "../models/report-export-dto";
export function toReportExport(raw: ReportExportDto, company: CompanyId): ReportExportDetails {
  const selection = normalizeReportSelection(company, {
    ...raw.selection,
    kind: raw.selection.kind as ReportKind,
    parameters: {
      ...raw.selection.parameters,
      companies: raw.selection.parameters.companies as CompanyId[],
    },
  });
  const created = Temporal.Instant.from(raw.createdAt),
    updated = Temporal.Instant.from(raw.updatedAt),
    expiry = Temporal.Instant.from(raw.expiresAt);
  if (
    raw.companyId.toLowerCase() !== company ||
    !selection.ok ||
    !isReportExportCursor(raw.createdAt, raw.id) ||
    !isReportExportCursor(raw.updatedAt, raw.id) ||
    !isReportExportCursor(raw.expiresAt, raw.id) ||
    (raw.selection.kind === "HEADCOUNT" && raw.selection.parameters.companies.length === 0) ||
    (raw.dataset === "DETAIL" && !supportsReportDetail(selection.value.kind)) ||
    Temporal.Instant.compare(updated, created) < 0 ||
    Temporal.Instant.compare(expiry, created) <= 0 ||
    Temporal.Instant.compare(expiry, created.add({ hours: 168 })) > 0 ||
    (raw.status === "READY" && (raw.bytes === 0 || raw.jobStatus !== "SUCCEEDED")) ||
    (raw.retiredAt !== null &&
      (!raw.expired ||
        !["READY", "STOPPED"].includes(raw.status) ||
        !isReportExportCursor(raw.retiredAt, raw.id) ||
        Temporal.Instant.compare(raw.retiredAt, expiry) < 0 ||
        Temporal.Instant.compare(raw.retiredAt, updated) > 0))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase(),
    companyId: company,
    jobId: raw.jobId.toLowerCase(),
    selection: selection.value,
  });
}
const before = (a: ReportExportCursor, b: ReportExportCursor): boolean => {
  const order = Temporal.Instant.compare(a.createdAt, b.createdAt);
  return order < 0 || (order === 0 && a.id < b.id);
};
export function toReportExportHistory(
  raw: ReportExportHistoryDto,
  company: CompanyId,
  cursor: ReportExportCursor | null,
): ReportExportHistory {
  const items = raw.items.map((item) => toReportExport(item, company));
  const next = raw.next
    ? Object.freeze({ createdAt: raw.next.createdAt, id: raw.next.id.toLowerCase() })
    : null;
  let previous = cursor;
  for (const item of items) {
    if (previous && !before(item, previous)) throw new InvalidHttpResponseError();
    previous = item;
  }
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (next &&
      (items.length !== reportExportPageSize ||
        !previous ||
        next.id !== previous.id ||
        next.createdAt !== previous.createdAt))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), next });
}
