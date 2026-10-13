import type { ExpenseClaim } from "./expense-claim";
import type { ExpenseClaimChange } from "./expense-claim-history";
import type { ExpensePaymentProgress } from "./expense-payment-progress";
import type { ExpenseSubmissionSummary } from "./expense-submission-summary";
export type ExpenseClaimReview = Readonly<{
  claim: ExpenseClaim;
  history: Readonly<{ items: readonly ExpenseClaimChange[]; nextCursor: string | null }>;
  submissions: readonly ExpenseSubmissionSummary[];
  payments: readonly ExpensePaymentProgress[];
}>;
