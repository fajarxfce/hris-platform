import { z } from "zod";
import {
  payrollAmendmentActions,
  payrollAmendmentStatuses,
} from "../../domain/entities/payroll-amendment";
import { compensationEmployeeDto, compensationTermsDto } from "./compensation-dto";
import { payrollCalculationOutputDto, payrollRunFailureDto } from "./payroll-calculation-dto";
import { payrollInputTermsDto } from "./payroll-input-dto";
import { payrollWorkDaysDto } from "./payroll-work-dto";

const version = z.number().int().min(0).max(999),
  sourceRevision = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  month = z.string().regex(/^(?:202[4-9]|20[3-9]\d|2100)-(?:0[1-9]|1[0-2])$/u),
  instant = z.iso.datetime({ offset: true }),
  signedAmount = z.string().regex(/^-?(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u),
  reason = z.string().min(1).max(1000);
export const payrollAmendmentSourceDto = z.object({
  assessmentId: z.uuid(),
  revision: sourceRevision,
});
export const payrollAmendmentCorrectionDto = z.object({
  assessmentId: z.uuid(),
  compensation: compensationTermsDto.nullable(),
  input: payrollInputTermsDto.nullable(),
  workAdjustmentId: z.uuid().nullable().default(null),
});
export const payrollAmendmentTermsDto = z.object({
  sourceVersions: z.array(payrollAmendmentSourceDto).min(1).max(12),
  corrections: z.array(payrollAmendmentCorrectionDto).min(1).max(12),
});
export const payrollAmendmentSummaryDto = z.object({
  id: z.uuid(),
  firstAssessmentId: z.uuid(),
  firstMonth: month,
  employee: compensationEmployeeDto,
  version,
  status: z.enum(payrollAmendmentStatuses),
  action: z.enum(payrollAmendmentActions),
  latestCalculationId: z.uuid().nullable(),
  latestReviewId: z.uuid().nullable(),
  latestPublicationId: z.uuid().nullable(),
  createdAt: instant,
  actorId: z.uuid(),
  recordedAt: instant,
  reason,
});
export const payrollAmendmentDto = z.object({
  summary: payrollAmendmentSummaryDto,
  terms: payrollAmendmentTermsDto,
});
export const payrollAmendmentPageDto = z.object({
  items: z.array(payrollAmendmentSummaryDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export const payrollAmendmentHistoryDto = z.object({
  items: z.array(payrollAmendmentSummaryDto).max(25),
  nextCursor: z
    .string()
    .regex(/^(?:0|[1-9]\d{0,2})$/u)
    .nullable(),
});
export const payrollAmendmentDifferenceDto = z.object({
  taxableGross: signedAmount,
  withheld: signedAmount,
  takeHome: signedAmount,
  employeeDeductions: signedAmount,
  employerCost: signedAmount,
});
export const payrollAmendmentCalculationSummaryDto = z.object({
  id: z.uuid(),
  amendmentId: z.uuid(),
  number: z.number().int().min(1).max(32),
  draftVersion: version,
  status: z.enum(["SUCCEEDED", "FAILED"]),
  assessmentCount: z.number().int().min(0).max(12),
  difference: payrollAmendmentDifferenceDto.nullable(),
  failure: payrollRunFailureDto.nullable(),
  actorId: z.uuid(),
  recordedAt: instant,
  reason,
});
export const payrollAmendmentPreviewDto = z.object({
  firstAssessmentId: z.uuid(),
  sourceVersions: z.array(payrollAmendmentSourceDto).min(1).max(12),
  calculations: z
    .array(
      z.object({
        source: payrollAmendmentSourceDto,
        employee: compensationEmployeeDto,
        month,
        compensation: compensationTermsDto,
        input: payrollInputTermsDto,
        workAdjustmentId: z.uuid().nullable().default(null),
        workDays: payrollWorkDaysDto.nullable().default(null),
        original: payrollCalculationOutputDto,
        calculation: payrollCalculationOutputDto,
        difference: payrollAmendmentDifferenceDto,
      }),
    )
    .min(1)
    .max(12),
  difference: payrollAmendmentDifferenceDto,
});
export const payrollAmendmentCalculationDto = z.object({
  summary: payrollAmendmentCalculationSummaryDto,
  preview: payrollAmendmentPreviewDto.nullable(),
});
export const payrollAmendmentReviewSummaryDto = z.object({
  id: z.uuid(),
  amendmentId: z.uuid(),
  number: z.number().int().min(1).max(8),
  amendmentVersion: version,
  calculationId: z.uuid(),
  approvalId: z.uuid(),
  referenceDate: z.iso.date(),
  timezone: z.string().min(1).max(80),
  exposure: z.string().regex(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u),
  currency: z.literal("IDR"),
  submittedBy: z.uuid(),
  submittedAt: instant,
  reason,
  status: z.enum(["PENDING", "APPROVED", "REJECTED", "WITHDRAWN"]),
  version: z.number().int().min(0).max(9),
});
export const payrollAmendmentDetailsDto = z.object({
  amendment: payrollAmendmentDto,
  history: payrollAmendmentHistoryDto,
  latestCalculation: payrollAmendmentCalculationSummaryDto.nullable(),
  latestReview: payrollAmendmentReviewSummaryDto.nullable(),
  availableActions: z.array(z.enum(["EDIT", "CALCULATE", "CANCEL", "SUBMIT"])).max(4),
});
export type PayrollAmendmentDto = z.infer<typeof payrollAmendmentDto>;
export type PayrollAmendmentSummaryDto = z.infer<typeof payrollAmendmentSummaryDto>;
export type PayrollAmendmentPageDto = z.infer<typeof payrollAmendmentPageDto>;
export type PayrollAmendmentHistoryDto = z.infer<typeof payrollAmendmentHistoryDto>;
export type PayrollAmendmentTermsDto = z.infer<typeof payrollAmendmentTermsDto>;
export type PayrollAmendmentProposalDto = z.input<typeof payrollAmendmentTermsDto>;
export type PayrollAmendmentDetailsDto = z.infer<typeof payrollAmendmentDetailsDto>;
export type PayrollAmendmentCalculationDto = z.infer<typeof payrollAmendmentCalculationDto>;
export type PayrollAmendmentCalculationSummaryDto = z.infer<
  typeof payrollAmendmentCalculationSummaryDto
>;
export type PayrollAmendmentPreviewDto = z.infer<typeof payrollAmendmentPreviewDto>;
export type PayrollAmendmentSearchDto = Readonly<{
  year: number;
  employeeId: string | null;
  query: string;
  status: string | null;
  after: string | null;
}>;
