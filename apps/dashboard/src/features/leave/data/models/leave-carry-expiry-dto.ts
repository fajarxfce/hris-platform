import { z } from "zod";
import { leaveInstantDto, leaveVersionDto } from "./leave-value-dto";

const count = z.number().int().min(0).max(5000);
const instant = leaveInstantDto.refine((value) => Number.isFinite(Date.parse(value)));
const status = z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]);
const failureCode = z
  .string()
  .regex(/^[a-z][a-z0-9_]{0,99}$/u)
  .nullable();
export const leaveCarryExpiryDto = z.object({
  id: z.uuid(),
  sourceKind: z.enum(["BATCH", "CLOSING"]),
  sourceId: z.uuid(),
  employeeId: z.uuid().nullable(),
  typeId: z.uuid(),
  typeCode: z.string().regex(/^[A-Z][A-Z0-9_-]{0,31}$/u),
  typeName: z.string().min(1).max(200),
  year: z.number().int().min(1901).max(2200),
  timezone: z
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
    }),
  validThrough: z.iso.date(),
  expiresAt: instant,
  actorId: z.uuid(),
  createdAt: instant,
  reason: z.string().min(1).max(1000),
  totalEmployees: count.min(1),
  jobId: z.uuid(),
  version: z.number().int().min(0).max(7),
  status,
  completedEmployees: count,
  failureCode,
});
export const leaveCarryExpiryPageDto = z.object({
  items: z.array(leaveCarryExpiryDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const leaveCarryExpiryDetailsDto = z.object({
  expiry: leaveCarryExpiryDto,
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
    id: z.uuid(),
    status,
    completedItems: z.number().int().min(0).max(5001),
    totalItems: z.number().int().min(1).max(5001),
    attempts: leaveVersionDto,
    version: leaveVersionDto,
    failureCode,
    scheduledFor: instant.nullable(),
    availableAt: instant,
    finishedAt: instant.nullable(),
  }),
  results: z.object({
    items: z
      .array(
        z.object({
          ordinal: count.min(1),
          employeeId: z.uuid(),
          jobId: z.uuid(),
          status: z.enum(["EXPIRED", "UNCHANGED", "NO_AWARD"]),
          expiredDays: z
            .string()
            .max(5)
            .regex(/^(?:0|[1-9][0-9]{0,2})(?:[.]5)?$/u)
            .refine((value) => Number(value) <= 366),
          ledgerId: z.uuid().nullable(),
          recordedAt: instant,
        }),
      )
      .max(20),
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
export type LeaveCarryExpiryDto = z.infer<typeof leaveCarryExpiryDto>;
export type LeaveCarryExpiryPageDto = z.infer<typeof leaveCarryExpiryPageDto>;
export type LeaveCarryExpiryDetailsDto = z.infer<typeof leaveCarryExpiryDetailsDto>;
export type LeaveCarryExpiryResumeDto = Readonly<{ expectedVersion: number; reason: string }>;
