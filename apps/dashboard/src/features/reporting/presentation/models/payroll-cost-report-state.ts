import type { Failure } from "../../../../core/domain/result";
import type { PayrollCostReport } from "../../domain/entities/payroll-cost-report";

export type PayrollCostReportState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: PayrollCostReport | null;
  failure: Failure | null;
}>;

export const initialPayrollCostReportState: PayrollCostReportState = {
  stage: "idle",
  report: null,
  failure: null,
};
