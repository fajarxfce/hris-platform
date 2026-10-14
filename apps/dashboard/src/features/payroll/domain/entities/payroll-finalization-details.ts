import type { PayrollFinalization } from "./payroll-finalization";
import type { PayrollReviewTotals } from "./payroll-review-totals";
import type { PayrollRun } from "./payroll-run";
import type { PayrollRunJob } from "./payroll-run-details";
export type PayrollFinalizationDetails = Readonly<{
  finalization: PayrollFinalization;
  run: PayrollRun;
  totals: PayrollReviewTotals;
  job: PayrollRunJob;
}>;
