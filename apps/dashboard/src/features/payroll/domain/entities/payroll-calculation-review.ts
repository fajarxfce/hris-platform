import type { Failure } from "../../../../core/domain/result";
import type { PayrollPeriod } from "./payroll-period";
import type { PayrollRun } from "./payroll-run";
export type PayrollCalculationSource = Readonly<{
  id: string;
  month: string;
  version: number;
  closed: boolean;
  jobId: string | null;
  timezone: string | null;
}>;
export type PayrollCalculationPolicy = Readonly<{
  version: number;
  appliedRevision: number;
  effectiveFrom: string;
  effectiveUntil: string;
  incomeTaxRuleId: string;
  insuranceRuleId: string;
}>;
export type PayrollCalculationReview = Readonly<{
  period: PayrollPeriod;
  source: PayrollCalculationSource | null;
  policy: PayrollCalculationPolicy | null;
  runCount: number;
  blockingIssues: readonly Failure[];
  availableActions: readonly "START"[];
}>;
export type PayrollCalculations = Readonly<{
  review: PayrollCalculationReview;
  runs: readonly PayrollRun[];
}>;
