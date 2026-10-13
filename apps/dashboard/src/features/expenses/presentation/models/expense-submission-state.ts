import type { Failure } from "../../../../core/domain/result";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
export type ExpenseSubmissionState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  submission: ExpenseSubmission | null;
  failure: Failure | null;
}>;
export const initialExpenseSubmissionState: ExpenseSubmissionState = {
  stage: "loading",
  submission: null,
  failure: null,
};
