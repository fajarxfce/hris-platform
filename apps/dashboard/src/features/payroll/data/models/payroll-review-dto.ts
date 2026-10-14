import { z } from "zod";
import { payrollRunDto, payrollRunFailureDto } from "./payroll-calculation-dto";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  money = z.string().regex(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u),
  signedMoney = z.string().regex(/^-?(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u),
  status = z.enum(["PENDING", "APPROVED", "REJECTED", "WITHDRAWN"]);
export const payrollReviewTotalsDto = z.object({
  employeeCount: z.number().int().min(0).max(5000),
  taxableGross: money,
  withheld: signedMoney,
  takeHome: money,
  currency: z.literal("IDR"),
});
export const payrollReviewDto = z.object({
  id: z.uuid(),
  runId: z.uuid(),
  number: z.number().int().min(1).max(8),
  runVersion: z.number().int().min(0).max(24),
  approvalId: z.uuid(),
  referenceDate: z.iso.date(),
  totals: payrollReviewTotalsDto,
  submittedBy: z.uuid(),
  submittedAt: z.iso.datetime({ offset: true }),
  reason: z.string().min(1).max(1000),
  status,
  version: z.number().int().min(0).max(9),
});
export const payrollReviewsDto = z.object({
  items: z.array(payrollReviewDto).max(8),
  nextCursor: z.string().max(1).nullable(),
});
export const payrollReviewPreparationDto = z.object({
  run: payrollRunDto,
  totals: payrollReviewTotalsDto,
  latestReview: payrollReviewDto.nullable(),
  blockingIssues: z.array(payrollRunFailureDto).max(10),
  availableActions: z.array(z.literal("SUBMIT")).max(1),
});
export const payrollReviewDetailsDto = z.object({
  review: payrollReviewDto,
  run: payrollRunDto,
  approval: z.object({
    id: z.uuid(),
    status: z.enum(["PENDING", "BLOCKED", "APPROVED", "REJECTED", "CANCELLED"]),
    version,
    currentStep: z.number().int().min(0).max(7),
    templateId: z.uuid(),
    templateRevision: version,
    stages: z.array(z.array(z.uuid()).max(200)).min(1).max(8),
    authorId: z.uuid(),
    excludedAccountIds: z.array(z.uuid()).max(200),
  }),
  changes: z
    .array(
      z.object({
        revision: z.number().int().min(0).max(9),
        action: z.enum(["SUBMITTED", "APPROVED", "REJECTED", "WITHDRAWN"]),
        status,
        approvalVersion: version,
        step: z.number().int().min(0).max(7).nullable(),
        actorId: z.uuid(),
        decidingFor: z.uuid().nullable(),
        reason: z.string().max(1000),
        at: z.iso.datetime({ offset: true }),
      }),
    )
    .min(1)
    .max(10),
  accounts: z.array(z.object({ id: z.uuid(), displayName: z.string().min(1).max(200) })).max(2000),
  finalizationActive: z.boolean(),
  availableActions: z.array(z.enum(["APPROVE", "REJECT", "WITHDRAW"])).max(3),
});
export type PayrollReviewDto = z.infer<typeof payrollReviewDto>;
export type PayrollReviewsDto = z.infer<typeof payrollReviewsDto>;
export type PayrollReviewPreparationDto = z.infer<typeof payrollReviewPreparationDto>;
export type PayrollReviewDetailsDto = z.infer<typeof payrollReviewDetailsDto>;

export const payrollReviewReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(9),
});
export type PayrollReviewReceiptDto = z.infer<typeof payrollReviewReceiptDto>;
export type PayrollReviewSubmissionDto = Readonly<{
  id: string;
  expectedRunVersion: number;
  reason: string;
}>;
export type PayrollReviewWithdrawalDto = Readonly<{
  expectedVersion: number;
  expectedApprovalVersion: number;
  reason: string;
}>;
export type PayrollReviewDecisionDto = PayrollReviewWithdrawalDto &
  Readonly<{ decision: "APPROVE" | "REJECT" }>;
