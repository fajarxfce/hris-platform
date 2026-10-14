import { z } from "zod";
import { payrollInputStatuses } from "../../domain/entities/payroll-input";
import { payrollInputActions } from "../../domain/entities/payroll-input-review";
import {
  payrollDayDispositions,
  payrollDayPortions,
  payrollHolidayKinds,
  payrollPriorHolidayPayments,
} from "../../domain/entities/payroll-input-terms";

const version = z.number().int().min(0).max(999);
const sourceVersion = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const month = z.string().regex(/^\d{4}-(?:0[1-9]|1[0-2])$/u);
const amount = z.string().regex(/^(?:0|[1-9]\d{0,10})(?:\.\d{1,2})?$/u);
const reference = z.string().min(1).max(200);
const code = z.string().regex(/^[A-Z][A-Z0-9_]{0,31}$/u);
const date = z.string().length(10);
export const payrollInputTermsDto = z.object({
  earnings: z
    .array(z.object({ code, name: z.string().min(1).max(100), amount, taxable: z.boolean() }))
    .max(40),
  deductions: z.array(z.object({ code, name: z.string().min(1).max(100), amount })).max(20),
  nonCashTaxable: amount,
  scheduledMonthUnits: z.string().min(1).max(4).nullable(),
  dayResolutions: z
    .array(
      z.object({
        workDate: date,
        portion: z.enum(payrollDayPortions),
        disposition: z.enum(payrollDayDispositions),
        reference,
      }),
    )
    .max(62),
  holidayAllowance: z
    .object({
      kind: z.enum(payrollHolidayKinds),
      holidayDate: date,
      continuousServiceFrom: date,
      priorPayment: z.enum(payrollPriorHolidayPayments),
      reviewReference: reference,
      promisedAmount: amount.nullable(),
    })
    .nullable(),
  reviewReference: reference,
});
const employee = z.object({
  id: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  name: z.string().min(1).max(200),
  employmentVersion: sourceVersion,
});
export const payrollInputEmployeeDto = employee.extend({
  inputVersion: version.nullable(),
  inputStatus: z.enum(payrollInputStatuses).nullable(),
});
export const payrollInputEmployeesDto = z.object({
  items: z.array(payrollInputEmployeeDto).max(50),
  nextCursor: z.string().min(1).max(32).nullable(),
});
export const payrollInputSummaryDto = z.object({
  id: z.uuid(),
  employeeId: z.uuid(),
  earningsMonth: month,
  version,
  status: z.enum(payrollInputStatuses),
  preparedBy: z.uuid(),
  verifiedBy: z.uuid().nullable(),
  recordedAt: z.iso.datetime({ offset: true }),
  reason: z.string().min(1).max(1000),
});
export const payrollInputDto = payrollInputSummaryDto.extend({
  workJobId: z.uuid(),
  workPeriodVersion: sourceVersion,
  employmentVersion: sourceVersion,
  terms: payrollInputTermsDto,
});
export const payrollInputReviewDto = z.object({
  earningsMonth: month,
  employee,
  source: z
    .object({
      periodId: z.uuid(),
      earningsMonth: month,
      version: sourceVersion,
      closed: z.boolean(),
      jobId: z.uuid().nullable(),
      includesEmployee: z.boolean(),
    })
    .nullable(),
  current: payrollInputDto.nullable(),
  history: z.object({
    items: z.array(payrollInputSummaryDto).max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
  frozen: z.boolean(),
  availableActions: z.array(z.enum(payrollInputActions)).max(2),
});
export const payrollInputReceiptDto = z.object({ id: z.uuid(), version });
export type PayrollInputTermsDto = z.infer<typeof payrollInputTermsDto>;
export type PayrollInputEmployeesDto = z.infer<typeof payrollInputEmployeesDto>;
export type PayrollInputSummaryDto = z.infer<typeof payrollInputSummaryDto>;
export type PayrollInputDto = z.infer<typeof payrollInputDto>;
export type PayrollInputReviewDto = z.infer<typeof payrollInputReviewDto>;
export type PayrollInputReceiptDto = z.infer<typeof payrollInputReceiptDto>;
export type PayrollInputChangeDto = {
  workJobId: string;
  expectedWorkPeriodVersion: number;
  expectedEmploymentVersion: number;
  expectedVersion: number | null;
  terms: PayrollInputTermsDto;
  reason: string;
};
export type PayrollInputVerificationDto = { expectedVersion: number; reason: string };
