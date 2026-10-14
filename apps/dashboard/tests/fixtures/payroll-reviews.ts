import type {
  PayrollReviewDetailsDto,
  PayrollReviewDto,
  PayrollReviewPreparationDto,
} from "../../src/features/payroll/data/models/payroll-review-dto";
import { calculationActor, calculationId, calculationRun } from "./payroll-calculations";
export const reviewId = "ed000000-0000-4000-8000-000000000001",
  reviewApproval = "ed100000-0000-4000-8000-000000000001",
  reviewFirst = "ed200000-0000-4000-8000-000000000001",
  reviewSecond = "ed200000-0000-4000-8000-000000000002";
export function payrollReview(changes: Partial<PayrollReviewDto> = {}): PayrollReviewDto {
  return {
    id: reviewId,
    runId: calculationId,
    number: 1,
    runVersion: 1,
    approvalId: reviewApproval,
    referenceDate: "2026-09-30",
    totals: {
      employeeCount: 1,
      taxableGross: "10000000.25",
      withheld: "-0.50",
      takeHome: "10000000.75",
      currency: "IDR",
    },
    submittedBy: calculationActor,
    submittedAt: "2026-10-01T00:02:00Z",
    reason: "Review the retained calculation",
    status: "PENDING",
    version: 0,
    ...changes,
  };
}
export function payrollReviewPreparation(): PayrollReviewPreparationDto {
  return {
    run: calculationRun(),
    totals: payrollReview().totals,
    latestReview: null,
    blockingIssues: [],
    availableActions: ["SUBMIT"],
  };
}
export function payrollReviewDetails(): PayrollReviewDetailsDto {
  return {
    review: payrollReview(),
    run: calculationRun(),
    approval: {
      id: reviewApproval,
      status: "PENDING",
      version: 0,
      currentStep: 0,
      templateId: "ed300000-0000-4000-8000-000000000001",
      templateRevision: 0,
      stages: [[reviewFirst], [reviewSecond]],
      authorId: calculationActor,
      excludedAccountIds: [calculationActor],
    },
    changes: [
      {
        revision: 0,
        action: "SUBMITTED",
        status: "PENDING",
        approvalVersion: 0,
        step: null,
        actorId: calculationActor,
        decidingFor: null,
        reason: "Review the retained calculation",
        at: "2026-10-01T00:02:00Z",
      },
    ],
    accounts: [
      { id: calculationActor, displayName: "Payroll Preparer" },
      { id: reviewFirst, displayName: "Payroll Reviewer One" },
      { id: reviewSecond, displayName: "Payroll Reviewer Two" },
    ],
    finalizationActive: false,
    availableActions: ["WITHDRAW"],
  };
}
