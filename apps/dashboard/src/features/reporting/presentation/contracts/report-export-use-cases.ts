import type { DownloadReportExport } from "../../domain/usecases/download-report-export";
import type { ManageReportExports } from "../../domain/usecases/manage-report-exports";
import type { ReadReportExports } from "../../domain/usecases/read-report-exports";
export type ReportExportUseCases = Readonly<{
  readExports: Pick<ReadReportExports, "list" | "get">;
  manageExports: Pick<ManageReportExports, "start" | "change">;
  downloadExport: Pick<DownloadReportExport, "execute">;
}>;
