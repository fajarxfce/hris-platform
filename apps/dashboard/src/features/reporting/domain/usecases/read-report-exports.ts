import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportExportCursor } from "../entities/report-export";
import { isReportExportCursor } from "../policies/report-export-policy";
import type { ReportExportRepository } from "../repositories/report-export-repository";
export class ReadReportExports {
  constructor(private readonly exports: ReportExportRepository) {}
  list(access: CompanyAccess, before: ReportExportCursor | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (before && !isReportExportCursor(before.createdAt, before.id))
      return Promise.resolve(failed("invalid_pagination"));
    return this.exports.list(
      access.companyId,
      before ? Object.freeze({ ...before, id: before.id.toLowerCase() }) : null,
      signal,
    );
  }
  get(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(id)) return Promise.resolve(failed("report_export_not_found"));
    return this.exports.get(access.companyId, id.toLowerCase(), signal);
  }
}
