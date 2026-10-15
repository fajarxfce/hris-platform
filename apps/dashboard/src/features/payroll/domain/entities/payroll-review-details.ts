import type { PayrollReview, PayrollReviewStatus } from "./payroll-review";
import type { PayrollReviewAccount, PayrollReviewApproval } from "./payroll-review-approval";
import type { PayrollReviewAction } from "./payroll-review-command";
import type { PayrollRun } from "./payroll-run";
export type PayrollReviewDetails = Readonly<{
  review: PayrollReview;
  run: PayrollRun;
  approval: PayrollReviewApproval;
  changes: readonly Readonly<{
    revision: number;
    action: "SUBMITTED" | "APPROVED" | "REJECTED" | "WITHDRAWN";
    status: PayrollReviewStatus;
    approvalVersion: number;
    step: number | null;
    actorId: string;
    decidingFor: string | null;
    reason: string;
    at: string;
  }>[];
  accounts: readonly PayrollReviewAccount[];
  finalizationActive: boolean;
  availableActions: readonly PayrollReviewAction[];
}>;
