import type { ExpenseClaim } from "./expense-claim";
export type ExpenseClaimActionSnapshot = Readonly<
  Pick<
    ExpenseClaim,
    "id" | "companyId" | "version" | "status" | "submissionCount" | "latestSubmissionId"
  > & { lineCount: number }
>;
export function expenseClaimActionSnapshot(claim: ExpenseClaim): ExpenseClaimActionSnapshot {
  return Object.freeze({
    id: claim.id,
    companyId: claim.companyId,
    version: claim.version,
    status: claim.status,
    submissionCount: claim.submissionCount,
    latestSubmissionId: claim.latestSubmissionId,
    lineCount: claim.draft.lines.length,
  });
}
