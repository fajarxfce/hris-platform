import { z } from "zod";
import {
  payrollRunDetailsDto,
  payrollRunDto,
  payrollRunFailureDto,
} from "./payroll-calculation-dto";
import { payrollReviewDto, payrollReviewTotalsDto } from "./payroll-review-dto";

const version = z.number().int().min(1).max(Number.MAX_SAFE_INTEGER);
export const payrollFinalizationDto = z.object({
  id: z.uuid(),
  runId: z.uuid(),
  reviewId: z.uuid(),
  runVersion: z.number().int().min(0).max(23),
  reviewVersion: z.number().int().min(1).max(8),
  approvalVersion: version,
  number: z.number().int().min(1).max(8),
  jobId: z.uuid(),
  actorId: z.uuid(),
  requestedAt: z.iso.datetime({ offset: true }),
  reason: z.string().min(1).max(1000),
  companyCode: z.string().min(1).max(32),
  companyName: z.string().min(1).max(200),
  publishedAt: z.iso.datetime({ offset: true }).nullable(),
});
export const payrollPublicationPageDto = z.object({
  items: z.array(payrollRunDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export const payrollFinalizationsDto = z.object({
  items: z.array(payrollFinalizationDto).max(8),
  nextCursor: z.string().max(1).nullable(),
});
export const payrollFinalizationPreparationDto = z.object({
  run: payrollRunDto,
  totals: payrollReviewTotalsDto,
  latestReview: payrollReviewDto.nullable(),
  approvalVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER).nullable(),
  latestFinalization: payrollFinalizationDto.nullable(),
  latestJobStatus: payrollRunDetailsDto.shape.job.shape.status.nullable(),
  blockingIssues: z.array(payrollRunFailureDto).max(10),
  availableActions: z.array(z.literal("START")).max(1),
});
export const payrollFinalizationDetailsDto = z.object({
  finalization: payrollFinalizationDto,
  run: payrollRunDto,
  totals: payrollReviewTotalsDto,
  job: payrollRunDetailsDto.shape.job.extend({
    totalItems: z.literal(1),
    completedItems: z.number().int().min(0).max(1),
  }),
});
export const payrollFinalizationReceiptDto = z.object({ id: z.uuid(), version: z.literal(0) });
export type PayrollFinalizationDto = z.infer<typeof payrollFinalizationDto>;
export type PayrollPublicationPageDto = z.infer<typeof payrollPublicationPageDto>;
export type PayrollFinalizationsDto = z.infer<typeof payrollFinalizationsDto>;
export type PayrollFinalizationPreparationDto = z.infer<typeof payrollFinalizationPreparationDto>;
export type PayrollFinalizationDetailsDto = z.infer<typeof payrollFinalizationDetailsDto>;
export type PayrollFinalizationReceiptDto = z.infer<typeof payrollFinalizationReceiptDto>;
export type PayrollPublicationSearchDto = Readonly<{
  from: string;
  until: string;
  status: "CALCULATED" | "FINALIZED" | null;
  after: string | null;
}>;
export type PayrollFinalizationStartDto = Readonly<{
  id: string;
  reviewId: string;
  expectedRunVersion: number;
  expectedReviewVersion: number;
  expectedApprovalVersion: number;
  reason: string;
}>;
