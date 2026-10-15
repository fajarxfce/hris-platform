import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentPage } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentInspection } from "../../domain/entities/payroll-amendment-search";

type ReadState<T> = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  data: T | null;
  failure: Failure | null;
}>;
export type PayrollAmendmentsState = ReadState<PayrollAmendmentPage>;
export type PayrollAmendmentState = ReadState<PayrollAmendmentInspection>;
export const initialPayrollAmendmentsState: PayrollAmendmentsState = {
  stage: "loading",
  data: null,
  failure: null,
};
export const initialPayrollAmendmentState: PayrollAmendmentState = {
  stage: "loading",
  data: null,
  failure: null,
};
