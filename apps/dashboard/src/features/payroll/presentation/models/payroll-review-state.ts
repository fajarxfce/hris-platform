import type { Failure } from "../../../../core/domain/result";
import type { PayrollReviewDetails } from "../../domain/entities/payroll-review-details";
import type { PayrollReviews } from "../../domain/entities/payroll-review-preparation";
export type PayrollReviewsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  data: PayrollReviews | null;
  failure: Failure | null;
}>;
export const initialPayrollReviewsState: PayrollReviewsState = {
  stage: "loading",
  data: null,
  failure: null,
};
export type PayrollReviewState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  data: PayrollReviewDetails | null;
  failure: Failure | null;
}>;
export const initialPayrollReviewState: PayrollReviewState = {
  stage: "loading",
  data: null,
  failure: null,
};
