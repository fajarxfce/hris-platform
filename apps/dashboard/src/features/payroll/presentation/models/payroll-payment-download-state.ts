import type { Failure } from "../../../../core/domain/result";
export type PayrollPaymentDownloadState = Readonly<{
  stage: "idle" | "downloading" | "started" | "failed" | "unavailable";
  failure: Failure | null;
}>;
export const initialPayrollPaymentDownload: PayrollPaymentDownloadState = Object.freeze({
  stage: "idle",
  failure: null,
});
