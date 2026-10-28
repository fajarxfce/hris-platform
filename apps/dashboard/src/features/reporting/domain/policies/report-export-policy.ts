import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { ReportKind } from "../entities/report-catalogue";
import type { ReportExportDetails } from "../entities/report-export";
import { reportFilterCommandWasRejected } from "./saved-report-filter-policy";
export const reportExportPageSize = 20;
export const supportsReportDetail = (kind: ReportKind): boolean =>
  ["LEAVE", "EXPENSES", "ATTENDANCE_CLOSINGS"].includes(kind);
export const isReportExportVersion = (version: number): boolean =>
  Number.isSafeInteger(version) && version >= 0 && version <= 9999;
export const isReportExportCursor = (at: string, id: string): boolean =>
  isUuid(id) &&
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/u.test(at) &&
  Number.isFinite(Date.parse(at));
export const reportExportExpired = (value: ReportExportDetails, now: number): boolean =>
  value.expired || value.retiredAt !== null || Date.parse(value.expiresAt) <= now;
export const canCancelReportExport = (value: ReportExportDetails): boolean =>
  value.retiredAt === null &&
  ["QUEUED", "RENDERING"].includes(value.status) &&
  value.version < 9999;
export const canResumeReportExport = (value: ReportExportDetails, now: number): boolean =>
  !reportExportExpired(value, now) &&
  value.status !== "READY" &&
  ["FAILED", "CANCELLED"].includes(value.jobStatus) &&
  value.attempt < 8 &&
  value.version < 9997;
export const reportExportCommandWasRejected = (failure: Failure): boolean =>
  reportFilterCommandWasRejected(failure) ||
  [
    "invalid_report_export",
    "unsupported_report_export_dataset",
    "report_export_not_found",
    "report_export_not_running",
    "report_export_not_stopped",
    "report_export_expired",
    "report_export_attempt_limit",
    "report_export_active_limit",
    "report_export_retention_limit",
    "job_queue_full",
  ].includes(failure.code);
