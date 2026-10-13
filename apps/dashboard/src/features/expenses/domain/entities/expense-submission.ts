import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalStatus } from "../../../approvals/domain/entities/approval-request";
import type { ExpenseClaimId, ExpenseClaimStatus } from "./expense-claim";
import type { ExpenseSubmissionReview } from "./expense-submission-review";
import type { ExpenseSubmissionSummary } from "./expense-submission-summary";
import type { ExpenseSubmittedLine } from "./expense-submitted-line";
export type ExpenseSubmission = ExpenseSubmissionSummary &
  Readonly<{
    companyId: CompanyId;
    claimId: ExpenseClaimId;
    employmentId: string;
    requesterId: string | null;
    employeeNumber: string;
    employeeName: string;
    description: string;
    lines: readonly ExpenseSubmittedLine[];
    makerIds: readonly string[];
    reason: string;
    approval: Readonly<{
      id: string;
      status: ApprovalStatus;
      version: number;
      currentStep: number;
      templateId: string;
      templateRevision: number;
      stages: readonly (readonly string[])[];
    }>;
    claimVersion: number;
    claimStatus: ExpenseClaimStatus;
    current: boolean;
    canReadClaim: boolean;
    availableActions: readonly "DECIDE"[];
    reviews: readonly ExpenseSubmissionReview[];
    duplicateReceiptDigests: readonly string[];
  }>;
