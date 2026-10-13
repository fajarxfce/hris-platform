import type { ExpenseSubmission } from "./expense-submission";
export type ExpenseDecisionSnapshot = Readonly<
  Pick<
    ExpenseSubmission,
    | "id"
    | "claimId"
    | "companyId"
    | "claimVersion"
    | "claimStatus"
    | "current"
    | "availableActions"
    | "duplicateReceiptDigests"
  > & { approvalVersion: number; approvalStatus: ExpenseSubmission["approval"]["status"] }
>;

export function expenseDecisionSnapshot(submission: ExpenseSubmission): ExpenseDecisionSnapshot {
  return Object.freeze({
    id: submission.id,
    claimId: submission.claimId,
    companyId: submission.companyId,
    claimVersion: submission.claimVersion,
    claimStatus: submission.claimStatus,
    current: submission.current,
    approvalVersion: submission.approval.version,
    approvalStatus: submission.approval.status,
    availableActions: Object.freeze([...submission.availableActions]),
    duplicateReceiptDigests: Object.freeze([...submission.duplicateReceiptDigests]),
  });
}
