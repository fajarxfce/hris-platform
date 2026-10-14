import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollReviewTotals } from "./payroll-review-totals";
export type PayrollReviewStatus = "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN";
export type PayrollReview = Readonly<{
  companyId: CompanyId;
  id: string;
  runId: string;
  number: number;
  runVersion: number;
  approvalId: string;
  referenceDate: string;
  totals: PayrollReviewTotals;
  submittedBy: string;
  submittedAt: string;
  reason: string;
  status: PayrollReviewStatus;
  version: number;
}>;
