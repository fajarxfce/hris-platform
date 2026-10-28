import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ReportSelection } from "../../domain/entities/report-selection";
import { reportPaths } from "./report-catalogue-view";
import { reportFilterParameters } from "./saved-report-filter-route";
export const reportExportHistoryPath = (company: CompanyId) =>
  `/reports/exports?${new URLSearchParams({ company })}`;
export const reportExportPath = (company: CompanyId, id: string) =>
  `/reports/exports/${encodeURIComponent(id)}?${new URLSearchParams({ company })}`;
export const reportExportSourcePath = (company: CompanyId, selection: ReportSelection) =>
  `${reportPaths[selection.kind]}?${reportFilterParameters(company, selection.parameters)}`;
/** The existing applied-selection link is generated locally from a canonical report result. */
export function exportAppliedReportPath(savedFilterTo: string): string | null {
  const prefix = "/reports/saved-filters/new?";
  return savedFilterTo.startsWith(prefix)
    ? `/reports/exports/new?${savedFilterTo.slice(prefix.length)}`
    : null;
}
