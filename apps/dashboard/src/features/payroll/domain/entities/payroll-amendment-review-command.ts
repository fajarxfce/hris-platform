import type { PayrollAmendmentCommand } from "./payroll-amendment-command";

export type PayrollAmendmentReviewSubmission = PayrollAmendmentCommand &
  Readonly<{
    reviewId: string;
    calculationId: string;
  }>;
export type PayrollAmendmentReviewWithdrawal = PayrollAmendmentCommand &
  Readonly<{
    amendmentId: string;
    expectedReviewVersion: number;
    expectedApprovalVersion: number;
  }>;
export type PayrollAmendmentReviewDecision = PayrollAmendmentReviewWithdrawal &
  Readonly<{
    decision: "APPROVE" | "REJECT";
  }>;
