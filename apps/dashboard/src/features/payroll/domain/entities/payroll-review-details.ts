import type { PayrollReview, PayrollReviewStatus } from "./payroll-review";
import type { PayrollReviewAction } from "./payroll-review-command";
import type { PayrollRun } from "./payroll-run";
export type PayrollReviewDetails = Readonly<{
  review: PayrollReview;
  run: PayrollRun;
  approval: Readonly<{
    id: string;
    status: "PENDING" | "BLOCKED" | "APPROVED" | "REJECTED" | "CANCELLED";
    version: number;
    currentStep: number;
    templateId: string;
    templateRevision: number;
    stages: readonly (readonly string[])[];
    authorId: string;
    excludedAccountIds: readonly string[];
  }>;
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
  accounts: readonly Readonly<{ id: string; displayName: string }>[];
  finalizationActive: boolean;
  availableActions: readonly PayrollReviewAction[];
}>;
