import type { Failure } from "../../../../core/domain/result";
import type {
  PayrollComparisonContext,
  PayrollRunComparison,
} from "../../domain/entities/payroll-run-comparison";
export type PayrollComparisonState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  context: PayrollComparisonContext | null;
  comparison: PayrollRunComparison | null;
  selected: string | null;
  failure: Failure | null;
}>;
export const initialPayrollComparisonState: PayrollComparisonState = {
  stage: "idle",
  context: null,
  comparison: null,
  selected: null,
  failure: null,
};
