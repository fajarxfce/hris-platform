import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadReportKind } from "../policies/report-catalogue-policy";
import { reportExportExpired } from "../policies/report-export-policy";
import type { ReportExportRepository } from "../repositories/report-export-repository";
export class DownloadReportExport {
  constructor(
    private readonly exports: ReportExportRepository,
    private readonly files: Pick<FileRepository, "downloadBinary">,
    private readonly now: () => number = Date.now,
  ) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read")) return failed("access_denied");
    if (!isUuid(id) || !isUuid(access.companyId)) return failed("report_export_not_found");
    const loaded = await this.exports.get(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!loaded.ok) return loaded;
    const value = loaded.value;
    if (!canReadReportKind(access.permissions, value.selection.kind))
      return failed("access_denied");
    if (reportExportExpired(value, this.now())) return failed("report_export_expired");
    if (value.status !== "READY") return failed("report_export_not_ready");
    const content = await this.exports.content(access.companyId, value, signal);
    if (!content.ok) {
      signal.throwIfAborted();
      return content;
    }
    try {
      signal.throwIfAborted();
      if (reportExportExpired(value, this.now())) return failed("report_export_expired");
      return await this.files.downloadBinary(
        Object.freeze({
          ...content.value,
          name: `report-${value.selection.kind.toLowerCase()}-${value.id}.csv`,
          mediaType: "text/csv",
        }),
        signal,
      );
    } finally {
      for (const part of content.value.parts) new Uint8Array(part).fill(0);
    }
  }
}
