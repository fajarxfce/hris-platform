import type { ExpenseClaim } from "./expense-claim";
import type { ExpenseDraftReferences } from "./expense-draft-references";
export type ExpenseDraftEditReview = Readonly<{
  claim: ExpenseClaim;
  references: ExpenseDraftReferences;
}>;
