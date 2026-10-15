import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  PayrollAmendmentDetails,
  PayrollAmendmentReviewSummary,
  PayrollAmendmentSummary,
} from "./payroll-amendment";
import type { PayrollAmendmentCalculationSummary } from "./payroll-amendment-calculation";
import type { PayrollReviewAccount, PayrollReviewApproval } from "./payroll-review-approval";

export type PayrollAmendmentReviewAction = "APPROVE" | "REJECT" | "WITHDRAW";
export type PayrollAmendmentReviewChange = Readonly<{
  revision: number;
  amendmentRevision: number;
  action: "SUBMITTED" | "APPROVED" | "REJECTED" | "WITHDRAWN";
  status: PayrollAmendmentReviewSummary["status"];
  approvalVersion: number;
  step: number | null;
  actorId: string;
  decidingFor: string | null;
  reason: string;
  recordedAt: string;
}>;
export type PayrollAmendmentReviewDetails = Readonly<{
  companyId: CompanyId;
  review: PayrollAmendmentReviewSummary;
  amendment: PayrollAmendmentSummary;
  calculation: PayrollAmendmentCalculationSummary;
  approval: PayrollReviewApproval;
  history: readonly PayrollAmendmentReviewChange[];
  accounts: readonly PayrollReviewAccount[];
  availableActions: readonly PayrollAmendmentReviewAction[];
}>;
export type PayrollAmendmentReviews = Readonly<{
  current: PayrollAmendmentDetails;
  reviews: readonly PayrollAmendmentReviewSummary[];
}>;
