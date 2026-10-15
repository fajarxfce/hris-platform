import type { PayrollAmendmentReviewSummary, PayrollAmendmentSummary } from "./payroll-amendment";
import type { PayrollAmendmentCalculationSummary } from "./payroll-amendment-calculation";

export type PayrollAmendmentPublicationPreparation = Readonly<{
  amendment: PayrollAmendmentSummary;
  review: PayrollAmendmentReviewSummary;
  calculation: PayrollAmendmentCalculationSummary;
  approvalVersion: number;
  number: number;
}>;
