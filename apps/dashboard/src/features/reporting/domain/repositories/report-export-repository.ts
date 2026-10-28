import type { BinaryFileContent } from "../../../../core/domain/files/binary-file";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type {
  ChangeReportExport,
  ReportExportCursor,
  ReportExportDetails,
  ReportExportHistory,
  ReportExportReceipt,
  StartReportExport,
} from "../entities/report-export";
export interface ReportExportRepository {
  list(
    company: CompanyId,
    before: ReportExportCursor | null,
    signal: AbortSignal,
  ): Promise<Result<ReportExportHistory>>;
  get(company: CompanyId, id: string, signal: AbortSignal): Promise<Result<ReportExportDetails>>;
  start(
    company: CompanyId,
    operation: OperationId,
    input: StartReportExport,
    signal: AbortSignal,
  ): Promise<Result<ReportExportReceipt>>;
  change(
    company: CompanyId,
    operation: OperationId,
    kind: "cancel" | "resume",
    input: ChangeReportExport,
    signal: AbortSignal,
  ): Promise<Result<ReportExportReceipt>>;
  content(
    company: CompanyId,
    value: ReportExportDetails,
    signal: AbortSignal,
  ): Promise<Result<BinaryFileContent>>;
}
