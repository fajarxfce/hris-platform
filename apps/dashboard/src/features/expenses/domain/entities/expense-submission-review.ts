import type { ExpenseClaimStatus } from "./expense-claim";
export type ExpenseSubmissionReview = Readonly<{
  claimVersion: number;
  approvalVersion: number;
  step: number;
  actorId: string;
  decidingFor: string;
  decision: "APPROVE" | "REJECT" | "RETURN";
  status: ExpenseClaimStatus;
  duplicateDigests: readonly string[];
  duplicatesAcknowledged: boolean;
  reason: string;
  decidedAt: string;
}>;
