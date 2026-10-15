import { z } from "zod";
import { leaveTypeDto } from "./leave-type-dto";
import { leaveDateDto, leaveInstantDto, leaveVersionDto } from "./leave-value-dto";

const kind = z.enum(["ACCRUAL", "YEAR_CLOSE"]);
const count = z.number().int().min(0).max(5000);
const instant = leaveInstantDto.refine((value) => Number.isFinite(Date.parse(value)));
const timezone = z
  .string()
  .min(1)
  .max(64)
  .refine((value) => {
    try {
      new Intl.DateTimeFormat("en", { timeZone: value });
      return true;
    } catch {
      return false;
    }
  });
const policy = leaveTypeDto
  .omit({ id: true, version: true, appliedRevision: true, active: true, effectiveFrom: true })
  .extend({
    typeId: z.uuid(),
    revision: leaveVersionDto,
  });
export const leaveBatchDto = z.object({
  id: z.uuid(),
  kind,
  typeId: z.uuid(),
  period: z.string().length(7),
  policy,
  policyVersion: leaveVersionDto,
  timezone,
  actorId: z.uuid(),
  createdAt: instant,
  reason: z.string().min(1).max(1000),
  totalEmployees: count.min(1),
  jobId: z.uuid(),
  status: z.enum(["RUNNING", "STOPPED", "COMPLETED"]),
  version: leaveVersionDto,
});
export const leaveBatchPageDto = z.object({
  items: z.array(leaveBatchDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const leaveBatchReviewDto = z.object({
  kind,
  period: z.string().length(7),
  companyDate: leaveDateDto,
  timezone,
  type: leaveTypeDto,
  totalEmployees: count.min(1),
  employeeIds: z.array(z.uuid()).min(1).max(5000),
});
const outcome = z.object({
  ordinal: count.min(1),
  employeeId: z.uuid(),
  jobId: z.uuid(),
  status: z.enum(["APPLIED", "UNCHANGED", "SKIPPED", "FAILED"]),
  resourceId: z.uuid().nullable(),
  failureCode: z
    .string()
    .regex(/^[a-z][a-z0-9_]{0,99}$/u)
    .nullable(),
  parameters: z
    .record(z.string().max(100), z.string().max(1000))
    .refine((value) => Object.keys(value).length <= 20),
  completedAt: instant,
});
export const leaveBatchDetailsDto = z.object({
  batch: leaveBatchDto,
  counts: z.object({
    applied: count,
    unchanged: count,
    skipped: count,
    failed: count,
    completed: count,
  }),
  attempts: z
    .array(
      z.object({
        jobId: z.uuid(),
        number: z.number().int().min(1).max(8),
        baseCompleted: count,
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
    attempts: leaveVersionDto,
    version: leaveVersionDto,
    cancellationRequested: z.boolean(),
    failureCode: z
      .string()
      .regex(/^[a-z][a-z0-9_]{0,99}$/u)
      .nullable(),
  }),
  results: z.object({
    items: z.array(outcome).max(20),
    nextCursor: z.string().min(1).max(4).nullable(),
  }),
  employees: z
    .array(
      z.object({
        id: z.uuid(),
        name: z.string().min(1).max(200).nullable(),
        number: z.string().min(1).max(32).nullable(),
      }),
    )
    .max(20),
  availableActions: z.array(z.literal("RESUME")).max(1),
});
export type LeaveBatchDto = z.infer<typeof leaveBatchDto>;
export type LeaveBatchPageDto = z.infer<typeof leaveBatchPageDto>;
export type LeaveBatchReviewDto = z.infer<typeof leaveBatchReviewDto>;
export type LeaveBatchDetailsDto = z.infer<typeof leaveBatchDetailsDto>;
export type LeaveBatchReviewQueryDto = Readonly<{
  kind: "ACCRUAL" | "YEAR_CLOSE";
  typeId: string;
  period: string;
}>;
export type LeaveAccrualBatchDto = Readonly<{
  id: string;
  typeId: string;
  month: string;
  expectedPolicyVersion: number;
  employeeIds: readonly string[];
  reason: string;
}>;
export type LeaveYearCloseBatchDto = Readonly<
  Omit<LeaveAccrualBatchDto, "month"> & { year: number }
>;
export type LeaveBatchResumeDto = Readonly<{ expectedVersion: number; reason: string }>;
