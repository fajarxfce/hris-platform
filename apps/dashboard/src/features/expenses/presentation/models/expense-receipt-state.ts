import type { Failure } from "../../../../core/domain/result";
export type ExpenseReceiptDownloadState = Readonly<{
  stage: "idle" | "downloading" | "started" | "failed" | "unavailable";
  revisionId: string | null;
  failure: Failure | null;
}>;
export const initialExpenseReceiptDownload: ExpenseReceiptDownloadState = {
  stage: "idle",
  revisionId: null,
  failure: null,
};
