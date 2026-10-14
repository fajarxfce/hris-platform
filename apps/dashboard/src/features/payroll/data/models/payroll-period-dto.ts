import { z } from "zod";

const month = z.string().regex(/^\d{4}-(?:0[1-9]|1[0-2])$/u),
  version = z.number().int().min(0).max(255);
const status = z.enum(["DRAFT", "PROCESSING", "CALCULATED", "FINALIZED", "CANCELLED"]);
export const payrollPeriodDto = z.object({
  id: z.uuid(),
  earningsMonth: month,
  plannedPaymentMonth: month,
  plannedPaymentDate: z.iso.date(),
  timezone: z.string().min(1).max(80),
  participantCount: z.number().int().min(1).max(5000),
  authorId: z.uuid(),
  createdAt: z.iso.datetime({ offset: true }),
  status,
  version,
  currentRunId: z.uuid().nullable(),
});
export const payrollPeriodsDto = z.object({
  items: z.array(payrollPeriodDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const payrollPeriodEmployeesDto = z.object({
  items: z
    .array(
      z.object({
        id: z.uuid(),
        employeeNumber: z.string().min(1).max(32),
        name: z.string().min(1).max(200),
        employmentVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
      }),
    )
    .max(50),
  nextCursor: z.string().min(1).max(32).nullable(),
});
export const payrollPeriodDetailsDto = z.object({
  period: payrollPeriodDto,
  members: z.object({
    items: z
      .array(
        z.object({
          employeeId: z.uuid(),
          employeeNumber: z.string().min(1).max(32),
          name: z.string().min(1).max(200),
          inputVersion: z.number().int().min(0).max(999).nullable(),
          inputStatus: z.enum(["DRAFT", "VERIFIED"]).nullable(),
        }),
      )
      .max(50),
    nextCursor: z.uuid().nullable(),
  }),
  history: z.object({
    items: z
      .array(
        z.object({
          version,
          status,
          actorId: z.uuid(),
          recordedAt: z.iso.datetime({ offset: true }),
          reason: z.string().min(1).max(1000),
          runId: z.uuid().nullable(),
        }),
      )
      .max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
});
export const payrollPeriodReceiptDto = z.object({ id: z.uuid(), version });
export type PayrollPeriodDto = z.infer<typeof payrollPeriodDto>;
export type PayrollPeriodsDto = z.infer<typeof payrollPeriodsDto>;
export type PayrollPeriodEmployeesDto = z.infer<typeof payrollPeriodEmployeesDto>;
export type PayrollPeriodDetailsDto = z.infer<typeof payrollPeriodDetailsDto>;
export type PayrollPeriodReceiptDto = z.infer<typeof payrollPeriodReceiptDto>;
export type PayrollPeriodCreationDto = Readonly<{
  id: string;
  earningsMonth: string;
  plannedPaymentDate: string;
  employeeIds: readonly string[];
  reason: string;
}>;
