import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentDetails } from "../../domain/entities/payroll-amendment";
import type { PayrollCommandState } from "./payroll-command-state";

export type PayrollAmendmentReviewReadState<T> = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  data: T | null;
  failure: Failure | null;
}>;
export const initialAmendmentReviewReadState = <T>(): PayrollAmendmentReviewReadState<T> => ({
  stage: "loading",
  data: null,
  failure: null,
});
export type PayrollAmendmentReviewSubmissionState = PayrollCommandState<PayrollAmendmentDetails> &
  Readonly<{ reviewId: string | null }>;
