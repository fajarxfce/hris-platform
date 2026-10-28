import type { BinaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type {
  ReportExportCursorDto,
  ReportExportDto,
  ReportExportHistoryDto,
  ReportExportReceiptDto,
  StartReportExportDto,
} from "../models/report-export-dto";
export interface ReportExportDataSource {
  list(
    company: string,
    before: ReportExportCursorDto | null,
    signal: AbortSignal,
  ): Promise<ReportExportHistoryDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<ReportExportDto>;
  start(
    company: string,
    operation: string,
    input: StartReportExportDto,
    signal: AbortSignal,
  ): Promise<ReportExportReceiptDto>;
  change(
    company: string,
    id: string,
    operation: string,
    kind: "cancel" | "resume",
    expectedVersion: number,
    signal: AbortSignal,
  ): Promise<ReportExportReceiptDto>;
  content(
    company: string,
    id: string,
    maximumBytes: number,
    signal: AbortSignal,
  ): Promise<BinaryResponseDto>;
}
