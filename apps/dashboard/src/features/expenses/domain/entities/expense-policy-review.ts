import type { ExpensePolicyDefinition } from "./expense-policy-definition";
import type { ExpensePolicyRevision } from "./expense-policy-revision";

export type ExpensePolicyReview = Readonly<{
  current: ExpensePolicyDefinition;
  history: Readonly<{ items: readonly ExpensePolicyRevision[]; nextCursor: string | null }>;
}>;
