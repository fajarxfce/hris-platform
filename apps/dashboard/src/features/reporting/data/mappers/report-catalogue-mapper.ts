import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import {
  type ReportCatalogue,
  type ReportKind,
  reportDefinitions,
} from "../../domain/entities/report-catalogue";
import type { ReportCatalogueDto } from "../models/report-catalogue-dto";

export function toReportCatalogue(value: ReportCatalogueDto, company: CompanyId): ReportCatalogue {
  if (
    value.companyId !== company ||
    new Set(value.reports.map((report) => report.kind)).size !== value.reports.length
  )
    throw new InvalidHttpResponseError();
  const reports = value.reports.map((report) => {
    if (
      !Object.hasOwn(reportDefinitions, report.kind) ||
      reportDefinitions[report.kind as ReportKind] !== report.definitionVersion
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      kind: report.kind as ReportKind,
      definitionVersion: report.definitionVersion,
    });
  });
  return Object.freeze({
    companyId: company,
    evaluatedAt: value.evaluatedAt,
    reports: Object.freeze(reports),
  });
}
