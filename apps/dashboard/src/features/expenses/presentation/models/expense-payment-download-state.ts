import type { Failure } from "../../../../core/domain/result";
export type ExpensePaymentDownloadState = Readonly<{
  stage: "idle" | "downloading" | "started" | "failed" | "unavailable";
  failure: Failure | null;
}>;
export const initialExpensePaymentDownload: ExpensePaymentDownloadState = Object.freeze({
  stage: "idle",
  failure: null,
});
