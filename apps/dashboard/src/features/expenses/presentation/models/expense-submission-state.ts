import type { Failure } from "../../../../core/domain/result";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
export type ExpenseReceiptDownloadState = Readonly<{
  stage: "idle" | "downloading" | "started" | "failed";
  revisionId: string | null;
  failure: Failure | null;
}>;
export const initialExpenseReceiptDownload: ExpenseReceiptDownloadState = {
  stage: "idle",
  revisionId: null,
  failure: null,
};
export type ExpenseSubmissionState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  submission: ExpenseSubmission | null;
  failure: Failure | null;
  download: ExpenseReceiptDownloadState;
}>;
export const initialExpenseSubmissionState: ExpenseSubmissionState = {
  stage: "loading",
  submission: null,
  failure: null,
  download: initialExpenseReceiptDownload,
};
