import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ChangeReportExport, StartReportExport } from "../entities/report-export";
import { canReadReportKind } from "../policies/report-catalogue-policy";
import { isReportExportVersion, supportsReportDetail } from "../policies/report-export-policy";
import { normalizeReportSelection } from "../policies/report-selection-policy";
import type { ReportExportRepository } from "../repositories/report-export-repository";
export class ManageReportExports {
  constructor(private readonly exports: ReportExportRepository) {}
  start(
    access: CompanyAccess,
    operation: OperationId,
    input: StartReportExport,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadReportKind(access.permissions, input.kind))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_report_export"));
    const normalized = normalizeReportSelection(access.companyId, input);
    if (!normalized.ok) return Promise.resolve(normalized);
    if (
      !["SUMMARY", "DETAIL"].includes(input.dataset) ||
      (input.dataset === "DETAIL" && !supportsReportDetail(input.kind))
    )
      return Promise.resolve(failed("unsupported_report_export_dataset"));
    return this.exports.start(
      access.companyId,
      operation,
      Object.freeze({ ...normalized.value, dataset: input.dataset }),
      signal,
    );
  }
  change(
    access: CompanyAccess,
    operation: OperationId,
    kind: "cancel" | "resume",
    input: ChangeReportExport,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(input.id) || !isUuid(operation) || !["cancel", "resume"].includes(kind))
      return Promise.resolve(failed("invalid_report_export"));
    if (!isReportExportVersion(input.expectedVersion))
      return Promise.resolve(failed("invalid_version"));
    // Server independently validates all original/current source grants on resume.
    return this.exports.change(
      access.companyId,
      operation,
      kind,
      Object.freeze({ ...input, id: input.id.toLowerCase() }),
      signal,
    );
  }
}
