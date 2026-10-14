import type { Failure } from "../../../../core/domain/result";
import type { PayrollFinalization } from "./payroll-finalization";
import type { PayrollReview } from "./payroll-review";
import type { PayrollReviewTotals } from "./payroll-review-totals";
import type { PayrollRun } from "./payroll-run";
import type { PayrollRunJob } from "./payroll-run-details";
export type PayrollFinalizationPreparation = Readonly<{
  run: PayrollRun;
  totals: PayrollReviewTotals;
  latestReview: PayrollReview | null;
  approvalVersion: number | null;
  latestFinalization: PayrollFinalization | null;
  latestJobStatus: PayrollRunJob["status"] | null;
  blockingIssues: readonly Failure[];
  availableActions: readonly "START"[];
}>;
export type PayrollFinalizations = Readonly<{
  preparation: PayrollFinalizationPreparation;
  finalizations: readonly PayrollFinalization[];
}>;
