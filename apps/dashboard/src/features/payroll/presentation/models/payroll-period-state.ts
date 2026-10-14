import type { Failure } from "../../../../core/domain/result";
import type { PayrollPeriods } from "../../domain/entities/payroll-period";
import type { PayrollPeriodDetails } from "../../domain/entities/payroll-period-details";
export type PayrollPeriodsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: PayrollPeriods | null;
  failure: Failure | null;
}>;
export const initialPayrollPeriodsState: PayrollPeriodsState = {
  stage: "loading",
  page: null,
  failure: null,
};
export type PayrollPeriodState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  details: PayrollPeriodDetails | null;
  failure: Failure | null;
}>;
export const initialPayrollPeriodState: PayrollPeriodState = {
  stage: "loading",
  details: null,
  failure: null,
};
