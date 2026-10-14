import { z } from "zod";
import {
  accidentRisks,
  insurancePrograms,
  taxTreatments,
} from "../../domain/entities/compensation-terms";
import {
  earningsRounding,
  overtimeEligibility,
  payrollWeekdays,
  payrollWorkWeeks,
  prorationBases,
  serviceMonthConventions,
} from "../../domain/entities/payroll-pay-basis";
import { ptkpStatuses, taxResidencies } from "../../domain/entities/payroll-tax-registration";

const version = z.number().int().min(0).max(999);
const month = z.string().regex(/^\d{4}-(?:0[1-9]|1[0-2])$/u);
const date = z.string().length(10);
const amount = z.string().regex(/^(?:0|[1-9]\d{0,10})(?:\.\d{1,2})?$/u);
const reference = z.string().min(1).max(200);
export const compensationEmployeeDto = z.object({
  id: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  name: z.string().min(1).max(200),
  employmentVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export const compensationTermsDto = z.object({
  basicSalary: amount,
  fixedEarnings: z
    .array(
      z.object({
        code: z.string().regex(/^[A-Z][A-Z0-9_]{0,31}$/u),
        name: z.string().min(1).max(100),
        amount,
        taxable: z.boolean(),
      }),
    )
    .max(20),
  treatment: z.enum(taxTreatments),
  tax: z.object({
    residency: z.enum(taxResidencies),
    ptkp: z.enum(ptkpStatuses),
    residenceCountry: z.string().regex(/^[A-Z]{2}$/u),
    subjectiveFrom: date.nullable(),
    subjectiveUntil: date.nullable(),
    verifiedOn: date,
    verificationReference: reference,
  }),
  insuranceWage: amount,
  insurancePrograms: z.array(z.enum(insurancePrograms)).max(5),
  insuranceExemptionReason: z.string().max(1000).nullable(),
  accidentRisk: z.enum(accidentRisks),
  additionalHealthDependents: z.number().int().min(0).max(5),
  additionalRetirementContribution: amount,
  qualifiedDonation: amount,
  otherNetDeduction: amount,
  payBasis: z
    .object({
      overtimeRuleId: reference,
      holidayAllowanceRuleId: reference,
      proration: z.enum(prorationBases),
      workWeek: z.enum(payrollWorkWeeks),
      shortestWorkDay: z.enum(payrollWeekdays).nullable(),
      overtimeEligibility: z.enum(overtimeEligibility),
      overtimeExemptionReference: reference.nullable(),
      regularNonFixedWage: amount,
      serviceMonthConvention: z.enum(serviceMonthConventions),
      rounding: z.enum(earningsRounding),
      reviewReference: reference,
    })
    .nullable(),
});
export const employeeCompensationDto = z.object({
  employeeId: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  version,
  appliedRevision: version,
  effectiveFrom: month,
  currency: z.literal("IDR"),
  terms: compensationTermsDto,
});
export const compensationEmployeesDto = z.object({
  items: z.array(compensationEmployeeDto).max(20),
  nextCursor: z.string().min(1).max(32).nullable(),
});
export const compensationReviewDto = z.object({
  asOf: month,
  employee: compensationEmployeeDto,
  current: employeeCompensationDto.nullable(),
  effective: employeeCompensationDto.nullable(),
  history: z.object({
    items: z
      .array(
        z.object({
          compensation: employeeCompensationDto,
          actorId: z.uuid(),
          reason: z.string().min(1).max(1000),
          recordedAt: z.iso.datetime({ offset: true }),
        }),
      )
      .max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
  canChange: z.boolean(),
});
export const compensationReceiptDto = z.object({ id: z.uuid(), version });
export type CompensationEmployeeDto = z.infer<typeof compensationEmployeeDto>;
export type CompensationTermsDto = z.infer<typeof compensationTermsDto>;
export type EmployeeCompensationDto = z.infer<typeof employeeCompensationDto>;
export type CompensationEmployeesDto = z.infer<typeof compensationEmployeesDto>;
export type CompensationReviewDto = z.infer<typeof compensationReviewDto>;
export type CompensationReceiptDto = z.infer<typeof compensationReceiptDto>;
