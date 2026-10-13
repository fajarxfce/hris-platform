import type { Failure } from "../../../../core/domain/result";
import type { ExpensePolicyReview } from "../../domain/entities/expense-policy-review";
import type { ExpensePolicyRevision } from "../../domain/entities/expense-policy-revision";

export type ExpensePolicyState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: ExpensePolicyReview | null;
  selectedRevision: ExpensePolicyRevision | null;
  failure: Failure | null;
}>;
export const initialExpensePolicyState: ExpensePolicyState = {
  stage: "loading",
  review: null,
  selectedRevision: null,
  failure: null,
};
