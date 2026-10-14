import type { Failure } from "../../../../core/domain/result";
import type { PayrollReview } from "./payroll-review";
import type { PayrollReviewTotals } from "./payroll-review-totals";
import type { PayrollRun } from "./payroll-run";
export type PayrollReviewPreparation = Readonly<{
  run: PayrollRun;
  totals: PayrollReviewTotals;
  latestReview: PayrollReview | null;
  blockingIssues: readonly Failure[];
  availableActions: readonly "SUBMIT"[];
}>;
export type PayrollReviews = Readonly<{
  preparation: PayrollReviewPreparation;
  reviews: readonly PayrollReview[];
}>;
