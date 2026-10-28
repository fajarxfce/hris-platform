import type { Failure } from "../../../../core/domain/result";
import type { ExpenseReport } from "../../domain/entities/expense-report";

export type ExpenseReportState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: ExpenseReport | null;
  failure: Failure | null;
}>;

export const initialExpenseReportState: ExpenseReportState = {
  stage: "idle",
  report: null,
  failure: null,
};
