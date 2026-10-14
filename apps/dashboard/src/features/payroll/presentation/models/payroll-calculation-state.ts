import type { Failure } from "../../../../core/domain/result";
import type { PayrollCalculations } from "../../domain/entities/payroll-calculation-review";
import type { PayrollRunDetails } from "../../domain/entities/payroll-run-details";
import type { PayrollRunEmployee } from "../../domain/entities/payroll-run-employee";
export type PayrollCalculationsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  data: PayrollCalculations | null;
  failure: Failure | null;
}>;
export const initialPayrollCalculationsState: PayrollCalculationsState = {
  stage: "loading",
  data: null,
  failure: null,
};
export type PayrollRunState = Readonly<{
  stage: "loading" | "updating" | "ready" | "unavailable";
  details: PayrollRunDetails | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;
export const initialPayrollRunState: PayrollRunState = {
  stage: "loading",
  details: null,
  failure: null,
  updatesPaused: false,
};
export type PayrollRunEmployeeState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  employee: PayrollRunEmployee | null;
  failure: Failure | null;
}>;
export const initialPayrollRunEmployeeState: PayrollRunEmployeeState = {
  stage: "loading",
  employee: null,
  failure: null,
};
