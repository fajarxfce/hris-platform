import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseDraft } from "./expense-draft";
import type { ExpenseSubmissionId } from "./expense-submission-summary";
export type ExpenseClaimId = string & { readonly expenseClaimId: unique symbol };
export const expenseClaimStatuses = [
  "DRAFT",
  "PENDING",
  "RETURNED",
  "APPROVED",
  "REJECTED",
  "CANCELLED",
] as const;
export type ExpenseClaimStatus = (typeof expenseClaimStatuses)[number];
export type ExpenseClaim = Readonly<{
  id: ExpenseClaimId;
  companyId: CompanyId;
  employmentId: string;
  createdBy: string;
  createdAt: string;
  version: number;
  draftRevision: number;
  status: ExpenseClaimStatus;
  draft: ExpenseDraft;
  submissionCount: number;
  latestSubmissionId: ExpenseSubmissionId | null;
}>;
