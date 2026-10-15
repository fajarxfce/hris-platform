import type {
  PayrollAmendmentReviewDetailsDto,
  PayrollAmendmentReviewSummaryDto,
} from "../../src/features/payroll/data/models/payroll-amendment-review-dto";
import { compensationActor } from "./compensations";
import {
  amendmentCalculation,
  amendmentCalculationId,
  amendmentId,
  amendmentSummary,
} from "./payroll-amendments";
import { payrollReviewDetails, reviewFirst, reviewSecond } from "./payroll-reviews";

export const amendmentReviewId = (index = 1) =>
  `fb100000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export function amendmentReview(
  changes: Partial<PayrollAmendmentReviewSummaryDto> = {},
): PayrollAmendmentReviewSummaryDto {
  return {
    id: amendmentReviewId(),
    amendmentId: amendmentId(),
    number: 1,
    amendmentVersion: 1,
    calculationId: amendmentCalculationId,
    approvalId: amendmentReviewId(100),
    referenceDate: "2026-10-01",
    timezone: "Asia/Jakarta",
    exposure: "1.00",
    currency: "IDR",
    submittedBy: compensationActor,
    submittedAt: "2026-10-01T01:00:02Z",
    reason: "Review the retained correction",
    status: "PENDING",
    version: 0,
    ...changes,
  };
}
export function amendmentReviewDetails(): PayrollAmendmentReviewDetailsDto {
  const review = amendmentReview(),
    approval = payrollReviewDetails().approval;
  return {
    review,
    amendment: {
      ...amendmentSummary(2),
      status: "IN_REVIEW",
      action: "SUBMITTED",
      latestReviewId: review.id,
      reason: review.reason,
    },
    calculation: amendmentCalculation().summary,
    approval: {
      ...approval,
      id: review.approvalId,
      authorId: compensationActor,
      excludedAccountIds: [compensationActor],
    },
    history: [
      {
        revision: 0,
        amendmentRevision: 2,
        action: "SUBMITTED",
        status: "PENDING",
        approvalVersion: 0,
        step: null,
        actorId: compensationActor,
        decidingFor: null,
        reason: review.reason,
        recordedAt: review.submittedAt,
      },
    ],
    accounts: [
      { id: compensationActor, displayName: "Amendment Preparer" },
      { id: reviewFirst, displayName: "Payroll Reviewer One" },
      { id: reviewSecond, displayName: "Payroll Reviewer Two" },
    ],
    availableActions: ["WITHDRAW"],
  };
}
