import type { Failure } from "../../../../core/domain/result";
import type {
  ReportExportDetails,
  ReportExportHistory,
  ReportExportReceipt,
} from "../../domain/entities/report-export";
export type ReportExportHistoryState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  history: ReportExportHistory | null;
  failure: Failure | null;
  older: boolean;
}>;
export const initialReportExportHistoryState: ReportExportHistoryState = {
  stage: "loading",
  history: null,
  failure: null,
  older: false,
};
export type ReportExportState = Readonly<{
  stage: "loading" | "ready" | "saving" | "unconfirmed" | "completed" | "unavailable" | "conflict";
  detail: ReportExportDetails | null;
  failure: Failure | null;
  receipt: ReportExportReceipt | null;
  command: "start" | "cancel" | "resume" | null;
  operationId: string | null;
  confirmation: "cancel" | "resume" | null;
  download: "idle" | "downloading" | "started";
}>;
export const initialReportExportState: ReportExportState = {
  stage: "loading",
  detail: null,
  failure: null,
  receipt: null,
  command: null,
  operationId: null,
  confirmation: null,
  download: "idle",
};
