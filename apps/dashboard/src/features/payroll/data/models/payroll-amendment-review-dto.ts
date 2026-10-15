import { z } from "zod";
import {
  payrollAmendmentCalculationSummaryDto,
  payrollAmendmentReviewSummaryDto,
  payrollAmendmentSummaryDto,
} from "./payroll-amendment-dto";
import { payrollReviewAccountDto, payrollReviewApprovalDto } from "./payroll-review-approval-dto";

export const payrollAmendmentReviewsDto = z.array(payrollAmendmentReviewSummaryDto).max(8);
export const payrollAmendmentReviewDetailsDto = z.object({
  review: payrollAmendmentReviewSummaryDto,
  amendment: payrollAmendmentSummaryDto,
  calculation: payrollAmendmentCalculationSummaryDto,
  approval: payrollReviewApprovalDto,
  history: z
    .array(
      z.object({
        revision: z.number().int().min(0).max(9),
        amendmentRevision: z.number().int().min(0).max(999),
        action: z.enum(["SUBMITTED", "APPROVED", "REJECTED", "WITHDRAWN"]),
        status: z.enum(["PENDING", "APPROVED", "REJECTED", "WITHDRAWN"]),
        approvalVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
        step: z.number().int().min(0).max(7).nullable(),
        actorId: z.uuid(),
        decidingFor: z.uuid().nullable(),
        reason: z.string().min(1).max(1000),
        recordedAt: z.iso.datetime({ offset: true }),
      }),
    )
    .min(1)
    .max(10),
  accounts: z.array(payrollReviewAccountDto).max(2000),
  availableActions: z.array(z.enum(["APPROVE", "REJECT", "WITHDRAW"])).max(3),
});
export type PayrollAmendmentReviewSummaryDto = z.infer<typeof payrollAmendmentReviewSummaryDto>;
export type PayrollAmendmentReviewDetailsDto = z.infer<typeof payrollAmendmentReviewDetailsDto>;
export type PayrollAmendmentReviewSubmissionDto = Readonly<{
  expectedVersion: number;
  calculationId: string;
  reason: string;
}>;
export type PayrollAmendmentReviewWithdrawalDto = Readonly<{
  expectedVersion: number;
  expectedReviewVersion: number;
  expectedApprovalVersion: number;
  reason: string;
}>;
export type PayrollAmendmentReviewDecisionDto = PayrollAmendmentReviewWithdrawalDto &
  Readonly<{ decision: "APPROVE" | "REJECT" }>;
