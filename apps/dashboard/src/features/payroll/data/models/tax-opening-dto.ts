import { z } from "zod";
import { ptkpStatuses, taxResidencies } from "../../domain/entities/payroll-tax-registration";
import { taxOpeningStatuses } from "../../domain/entities/tax-opening";
import { taxOpeningActions } from "../../domain/entities/tax-opening-review";

const version = z.number().int().min(0).max(999);
const year = z.number().int().min(2024).max(2100);
const amount = z.string().regex(/^(?:0|[1-9]\d{0,11})(?:\.\d{1,2})?$/u);
export const taxOpeningTermsDto = z.object({
  throughMonth: z.number().int().min(0).max(11),
  residency: z.enum(taxResidencies),
  ptkp: z.enum(ptkpStatuses),
  reference: z.string().min(1).max(200),
  history: z.object({
    taxableGross: amount,
    retirementContributions: amount,
    qualifiedDonations: amount,
    withheld: amount,
    employmentMonths: z.number().int().min(0).max(11),
    previousEmployerNet: amount,
    previousEmployerWithheld: amount,
  }),
});
const employee = z.object({
  id: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  name: z.string().min(1).max(200),
  employmentVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export const taxOpeningEmployeesDto = z.object({
  items: z
    .array(
      employee.extend({
        openingVersion: version.nullable(),
        openingStatus: z.enum(taxOpeningStatuses).nullable(),
      }),
    )
    .max(50),
  nextCursor: z.string().min(1).max(32).nullable(),
});
export const taxOpeningSummaryDto = z.object({
  id: z.uuid(),
  employeeId: z.uuid(),
  year,
  version,
  status: z.enum(taxOpeningStatuses),
  preparedBy: z.uuid(),
  verifiedBy: z.uuid().nullable(),
  recordedAt: z.iso.datetime({ offset: true }),
  reason: z.string().min(1).max(1000),
});
export const taxOpeningDto = taxOpeningSummaryDto.extend({ terms: taxOpeningTermsDto });
export const taxOpeningReviewDto = z.object({
  year,
  employee,
  current: taxOpeningDto.nullable(),
  history: z.object({
    items: z.array(taxOpeningSummaryDto).max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
  frozen: z.boolean(),
  availableActions: z.array(z.enum(taxOpeningActions)).max(2),
});
export const taxOpeningReceiptDto = z.object({ id: z.uuid(), version });
export type TaxOpeningTermsDto = z.infer<typeof taxOpeningTermsDto>;
export type TaxOpeningEmployeesDto = z.infer<typeof taxOpeningEmployeesDto>;
export type TaxOpeningSummaryDto = z.infer<typeof taxOpeningSummaryDto>;
export type TaxOpeningDto = z.infer<typeof taxOpeningDto>;
export type TaxOpeningReviewDto = z.infer<typeof taxOpeningReviewDto>;
export type TaxOpeningReceiptDto = z.infer<typeof taxOpeningReceiptDto>;
export type TaxOpeningChangeDto = {
  expectedEmploymentVersion: number;
  expectedVersion: number | null;
  terms: TaxOpeningTermsDto;
  reason: string;
};
export type TaxOpeningVerificationDto = { expectedVersion: number; reason: string };
