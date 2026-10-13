import type { ExpenseClaimId } from "./expense-claim";
import type { ExpenseDecision } from "./expense-decision";
import type { ExpenseSubmissionId } from "./expense-submission-summary";
export type ExpenseDecisionCommand = Readonly<{
  id: ExpenseSubmissionId;
  claimId: ExpenseClaimId;
  claimVersion: number;
  approvalVersion: number;
  decision: ExpenseDecision;
  reason: string;
  acknowledgeDuplicates: boolean;
}>;
