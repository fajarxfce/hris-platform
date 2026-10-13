import type { ExpenseClaimStatus } from "./expense-claim";
import type { ExpenseSubmissionId } from "./expense-submission-summary";
export const expenseChangeKinds = [
  "DRAFT_SAVED",
  "SUBMITTED",
  "WITHDRAWN",
  "REVIEWED",
  "CANCELLED",
] as const;
export type ExpenseClaimChange = Readonly<{
  version: number;
  draftRevision: number;
  status: ExpenseClaimStatus;
  kind: (typeof expenseChangeKinds)[number];
  actorId: string;
  reason: string;
  recordedAt: string;
  submissionId: ExpenseSubmissionId | null;
}>;
