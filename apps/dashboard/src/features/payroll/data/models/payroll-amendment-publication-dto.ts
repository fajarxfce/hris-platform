import { z } from "zod";
import { payrollAmendmentSummaryDto } from "./payroll-amendment-dto";
import { payrollRunDetailsDto } from "./payroll-calculation-dto";

const code = z
  .string()
  .regex(/^[a-z][a-z0-9_]{0,199}$/u)
  .nullable();
export const payrollAmendmentPublicationDto = z.object({
  id: z.uuid(),
  amendmentId: z.uuid(),
  reviewId: z.uuid(),
  calculationId: z.uuid(),
  amendmentVersion: z.number().int().min(0).max(996),
  reviewVersion: z.number().int().min(1).max(8),
  approvalVersion: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
  number: z.number().int().min(1).max(8),
  jobId: z.uuid(),
  actorId: z.uuid(),
  requestedAt: z.iso.datetime({ offset: true }),
  reason: z.string().min(1).max(1000),
  status: z.enum(["PENDING", "PUBLISHED", "STOPPED"]),
  completedAt: z.iso.datetime({ offset: true }).nullable(),
  failureCode: code,
  completedBy: z.uuid().nullable(),
});
export const payrollAmendmentPublicationDetailsDto = z.object({
  publication: payrollAmendmentPublicationDto,
  job: payrollRunDetailsDto.shape.job.extend({
    totalItems: z.literal(1),
    completedItems: z.number().int().min(0).max(1),
    failureCode: code,
  }),
  amendment: payrollAmendmentSummaryDto,
  availableActions: z.array(z.literal("RECOVER")).max(1),
});
export const payrollAmendmentPublicationsDto = z
  .array(payrollAmendmentPublicationDetailsDto)
  .max(8);
export type PayrollAmendmentPublicationDetailsDto = z.infer<
  typeof payrollAmendmentPublicationDetailsDto
>;
export type PayrollAmendmentPublicationStartDto = Readonly<{
  expectedVersion: number;
  reviewId: string;
  expectedReviewVersion: number;
  expectedApprovalVersion: number;
  calculationId: string;
  reason: string;
}>;
export type PayrollAmendmentPublicationRecoveryDto = Readonly<{
  expectedVersion: number;
  expectedJobVersion: number;
  reason: string;
}>;
