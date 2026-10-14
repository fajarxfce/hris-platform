import type { Failure } from "../../../../core/domain/result";
import type { PayrollInput } from "../../domain/entities/payroll-input";
import type { PayrollInputReview } from "../../domain/entities/payroll-input-review";
export type PayrollInputState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: PayrollInputReview | null;
  selected: PayrollInput | null;
  inspecting: boolean;
  failure: Failure | null;
}>;
export const initialPayrollInputState: PayrollInputState = {
  stage: "loading",
  review: null,
  selected: null,
  inspecting: false,
  failure: null,
};
