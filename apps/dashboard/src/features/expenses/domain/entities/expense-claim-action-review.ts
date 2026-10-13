import type { ExpenseClaim } from "./expense-claim";
import type { ExpenseDraftReferences } from "./expense-draft-references";
export type ExpenseClaimActionReview = Readonly<{
  claim: ExpenseClaim;
  references: ExpenseDraftReferences | null;
}>;
