import { z } from "zod";
import { payrollPeriodDto } from "./payroll-period-dto";

const reference = z.string().min(1).max(200),
  month = z.string().regex(/^(?:202[4-9]|20[3-9]\d|2100)-(?:0[1-9]|1[0-2])$/u),
  version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  revision = z.number().int().min(0).max(999),
  instant = z.iso.datetime({ offset: true }),
  money = z.string().regex(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u),
  signedMoney = z.string().regex(/^-?(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u),
  ratio = z.string().regex(/^(?:0|[1-9]\d{0,4})(?:\.\d{1,18})?$/u);
export const payrollRunFailureDto = z.object({
  kind: z.string().min(1).max(60),
  code: z.string().min(1).max(200),
  fields: z
    .record(z.string().max(200), z.string().max(500))
    .refine((v) => Object.keys(v).length <= 200),
  parameters: z
    .record(z.string().max(200), z.string().max(500))
    .refine((v) => Object.keys(v).length <= 200),
});
export const payrollCalculationPolicyDto = z.object({
  version: revision,
  appliedRevision: revision,
  effectiveFrom: month,
  effectiveUntil: month,
  incomeTaxRuleId: reference,
  insuranceRuleId: reference,
});
export const payrollCalculationReviewDto = z.object({
  period: payrollPeriodDto,
  source: z
    .object({
      id: z.uuid(),
      month,
      version,
      closed: z.boolean(),
      jobId: z.uuid().nullable(),
      timezone: z.string().min(1).max(80).nullable(),
    })
    .nullable(),
  policy: payrollCalculationPolicyDto.nullable(),
  runCount: z.number().int().min(0).max(20),
  blockingIssues: z.array(payrollRunFailureDto).max(10),
  availableActions: z.array(z.literal("START")).max(1),
});
export const payrollRunDto = z.object({
  id: z.uuid(),
  periodId: z.uuid(),
  number: z.number().int().min(1).max(20),
  earningsMonth: month,
  taxMonth: month,
  incomeDueDate: z.iso.date(),
  plannedPaymentDate: z.iso.date(),
  timezone: z.string().min(1).max(80),
  workJobId: z.uuid(),
  workPeriodVersion: version,
  policyRevision: revision,
  totalEmployees: z.number().int().min(1).max(5000),
  actorId: z.uuid(),
  reviewReference: reference,
  reason: z.string().min(1).max(1000),
  createdAt: instant,
  jobId: z.uuid(),
  status: z.enum(["PROCESSING", "STOPPED", "CALCULATED", "FINALIZED", "ABANDONED"]),
  version: z.number().int().min(0).max(25),
  processed: z.number().int().min(0).max(5000),
  succeeded: z.number().int().min(0).max(5000),
  failed: z.number().int().min(0).max(5000),
  finalizationId: z.uuid().nullable(),
});
export const payrollRunsDto = z.object({
  items: z.array(payrollRunDto).max(20),
  nextCursor: z.string().max(2).nullable(),
});
export const payrollRunItemDto = z.object({
  target: z.object({
    ordinal: z.number().int().min(1).max(5000),
    employeeId: z.uuid(),
    employeeNumber: z.string().min(1).max(32),
    employeeName: z.string().min(1).max(200),
    employmentVersion: version,
    compensationRevision: revision.nullable(),
    inputId: z.uuid().nullable(),
    inputRevision: revision.nullable(),
    taxOpeningId: z.uuid().nullable(),
    taxOpeningRevision: revision.nullable(),
    previousAssessmentId: z.uuid().nullable(),
  }),
  jobId: z.uuid(),
  completedAt: instant,
  failure: payrollRunFailureDto.nullable(),
  taxableGross: money.nullable(),
  withheld: signedMoney.nullable(),
  takeHome: money.nullable(),
});
export const payrollRunDetailsDto = z.object({
  run: payrollRunDto,
  period: payrollPeriodDto,
  attempts: z
    .array(
      z.object({
        jobId: z.uuid(),
        number: z.number().int().min(1).max(8),
        baseCompleted: z.number().int().min(0).max(5000),
        startedAt: instant,
        reason: z.string().min(1).max(1000),
      }),
    )
    .min(1)
    .max(8),
  job: z.object({
    jobId: z.uuid(),
    status: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
    completedItems: z.number().int().min(0).max(5001),
    totalItems: z.number().int().min(1).max(5001),
    attempts: z.number().int().min(0).max(100),
    version,
    cancellationRequested: z.boolean(),
    failureCode: z.string().max(200).nullable(),
  }),
  results: z.object({
    items: z.array(payrollRunItemDto).max(50),
    nextCursor: z
      .string()
      .regex(/^[1-9]\d{0,3}$/u)
      .nullable(),
  }),
  availableActions: z.array(z.enum(["CANCEL_JOB", "RESUME", "ABANDON"])).max(3),
});
export const payrollCalculationOutputDto = z.object({
  units: z.object({
    total: ratio,
    payable: ratio,
    unpaid: ratio,
    employedDates: z.array(z.iso.date()).max(31),
  }),
  earnings: z
    .array(
      z.object({
        kind: z.enum(["BASIC", "FIXED", "VARIABLE", "OVERTIME", "HOLIDAY"]),
        code: reference,
        name: reference.nullable(),
        amount: money,
        taxable: z.boolean(),
      }),
    )
    .max(104),
  overtime: z
    .array(
      z.object({
        date: z.iso.date(),
        calculation: z.object({
          ruleId: reference,
          dayKind: reference,
          displayHourlyWage: money,
          amount: money,
          segments: z
            .array(z.object({ minutes: z.number().int().min(1).max(1440), multiplier: ratio }))
            .max(24),
        }),
      }),
    )
    .max(31),
  holiday: z
    .object({
      ruleId: reference,
      holidayDate: z.iso.date(),
      dueDate: z.iso.date(),
      monthlyWage: money,
      statutoryAmount: money,
      employerTopUp: money,
      amount: money,
    })
    .nullable(),
  contributions: z
    .array(
      z.object({ program: reference, base: money, employeeAmount: money, employerAmount: money }),
    )
    .max(10),
  tax: z.object({
    ruleId: reference,
    taxableGross: money,
    taxAllowance: money,
    deductionAllowance: money,
    withheld: signedMoney,
    takeHome: money,
    category: reference.nullable(),
    effectiveRate: ratio.nullable(),
    annualNet: money.nullable(),
    annualizedNet: money.nullable(),
    annualTaxable: money.nullable(),
    annualTax: money.nullable(),
  }),
  employeeDeductions: signedMoney,
  employerCost: money,
});
export const payrollRunEmployeeDto = z.object({
  item: payrollRunItemDto,
  facts: z
    .object({
      month,
      incomeDueDate: z.iso.date(),
      plannedPaymentDate: z.iso.date(),
      policy: payrollCalculationPolicyDto,
      compensation: z.object({ basicSalary: money }),
      input: z.object({ reviewReference: reference }),
      taxHistory: z.object({ throughMonth: z.number().int().min(0).max(11), reference: reference }),
      workDays: z
        .array(
          z.object({
            date: z.iso.date(),
            schedule: z.enum(["WORK", "OFF", "UNASSIGNED"]),
            attendance: z.enum(["WORKED", "ABSENCE_RECORDED", "UNRECORDED", "OFF", "UNASSIGNED"]),
            officialHoliday: z.boolean(),
          }),
        )
        .max(31),
      leaveDays: z
        .array(
          z.object({
            requestId: z.uuid(),
            revision: version,
            date: z.iso.date(),
            portion: reference,
            paid: z.boolean(),
          }),
        )
        .max(62),
    })
    .nullable(),
  calculation: payrollCalculationOutputDto.nullable(),
});
export const payrollCalculationReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(25),
});
export type PayrollCalculationReviewDto = z.infer<typeof payrollCalculationReviewDto>;
export type PayrollRunDto = z.infer<typeof payrollRunDto>;
export type PayrollRunsDto = z.infer<typeof payrollRunsDto>;
export type PayrollRunItemDto = z.infer<typeof payrollRunItemDto>;
export type PayrollRunDetailsDto = z.infer<typeof payrollRunDetailsDto>;
export type PayrollRunEmployeeDto = z.infer<typeof payrollRunEmployeeDto>;
export type PayrollCalculationReceiptDto = z.infer<typeof payrollCalculationReceiptDto>;
export type PayrollCalculationStartDto = Readonly<{
  id: string;
  incomeDueDate: string;
  expectedPeriodVersion: number;
  expectedWorkPeriodVersion: number;
  expectedPolicyVersion: number;
  reviewReference: string;
  reason: string;
}>;
export type PayrollCalculationRecoveryDto = Readonly<{
  expectedVersion: number;
  expectedPeriodVersion: number;
  reason: string;
}>;
