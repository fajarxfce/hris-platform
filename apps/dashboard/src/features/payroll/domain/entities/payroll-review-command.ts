export type PayrollReviewAction = "APPROVE" | "REJECT" | "WITHDRAW";
export type PayrollReviewSubmission = Readonly<{
  id: string;
  expectedRunVersion: number;
  reason: string;
}>;
export type PayrollReviewWithdrawal = Readonly<{
  expectedVersion: number;
  expectedApprovalVersion: number;
  reason: string;
}>;
export type PayrollReviewDecision = PayrollReviewWithdrawal &
  Readonly<{
    decision: "APPROVE" | "REJECT";
  }>;
