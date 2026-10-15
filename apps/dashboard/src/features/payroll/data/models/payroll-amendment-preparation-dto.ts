import { z } from "zod";
import { compensationEmployeeDto, compensationTermsDto } from "./compensation-dto";
import {
  payrollAmendmentSourceDto,
  payrollAmendmentSummaryDto,
  type payrollAmendmentTermsDto,
} from "./payroll-amendment-dto";
import { payrollCalculationOutputDto } from "./payroll-calculation-dto";
import { payrollInputTermsDto } from "./payroll-input-dto";
export const payrollAmendmentCandidatesDto = z.object({
  items: z
    .array(
      z.object({
        assessmentId: z.uuid(),
        employeeId: z.uuid(),
        employeeNumber: z.string().min(1).max(64),
        employeeName: z.string().min(1).max(300),
        taxMonth: payrollAmendmentSummaryDto.shape.firstMonth,
        revision: payrollAmendmentSourceDto.shape.revision,
        publishedAt: z.iso.datetime({ offset: true }),
      }),
    )
    .max(50),
  nextCursor: z.uuid().nullable(),
});
export const payrollAmendmentPreparationDto = z.object({
  firstAssessmentId: z.uuid(),
  sourceVersions: z.array(payrollAmendmentSourceDto).min(1).max(12),
  assessments: z
    .array(
      z.object({
        source: payrollAmendmentSourceDto,
        employee: compensationEmployeeDto,
        month: payrollAmendmentSummaryDto.shape.firstMonth,
        compensation: compensationTermsDto,
        input: payrollInputTermsDto,
        calculation: payrollCalculationOutputDto,
      }),
    )
    .min(1)
    .max(12),
});
export type PayrollAmendmentCandidatesDto = z.infer<typeof payrollAmendmentCandidatesDto>;
export type PayrollAmendmentPreparationDto = z.infer<typeof payrollAmendmentPreparationDto>;
export type PayrollAmendmentSourceSearchDto = Readonly<{
  year: number;
  query: string;
  employeeId: string | null;
  after: string | null;
}>;
export type PayrollAmendmentSaveDto = z.infer<typeof payrollAmendmentTermsDto> & {
  firstAssessmentId: string;
  expectedVersion: number | null;
  reason: string;
};
export const payrollAmendmentReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(999),
});
export type PayrollAmendmentReceiptDto = z.infer<typeof payrollAmendmentReceiptDto>;
