import type { Failure } from "../../../../core/domain/result";
import type { PayrollInputEmployees } from "../../domain/entities/payroll-input-review";
export type PayrollInputEmployeesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: PayrollInputEmployees | null;
  failure: Failure | null;
}>;
export const initialPayrollInputEmployeesState: PayrollInputEmployeesState = {
  stage: "loading",
  page: null,
  failure: null,
};
