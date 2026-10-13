import type { Failure } from "../../../../core/domain/result";
import type { ExpensePolicyPage } from "../../domain/entities/expense-policy-definition";

export type ExpensePoliciesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: ExpensePolicyPage | null;
  failure: Failure | null;
}>;
export const initialExpensePoliciesState: ExpensePoliciesState = {
  stage: "loading",
  page: null,
  failure: null,
};
