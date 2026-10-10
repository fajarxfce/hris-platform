import { z } from "zod";
import { leaveDateDto, leaveInstantDto, leaveVersionDto } from "./leave-value-dto";

const days = z
  .string()
  .max(5)
  .regex(/^(?:0|[1-9]\d{0,2})(?:\.5)?$/u)
  .refine((value) => Number(value) <= 366);
const terms = z.object({
  name: z.string().min(1).max(200),
  paid: z.boolean(),
  allowPartialDays: z.boolean(),
  minServiceMonths: z.number().int().min(0).max(120),
  allowedContracts: z
    .array(z.enum(["PERMANENT", "FIXED_TERM"]))
    .min(1)
    .max(2),
  maxRequestDays: z.number().int().min(1).max(366),
  attachmentRequired: z.boolean(),
  accrual: z
    .object({
      frequency: z.enum(["MANUAL", "MONTHLY", "ANNUAL"]),
      daysPerPeriod: days,
      carryLimitDays: days,
    })
    .nullable(),
  active: z.boolean(),
  effectiveFrom: leaveDateDto,
});
export const leaveTypeDto = terms.extend({
  id: z.uuid(),
  code: z.string().regex(/^[A-Z][A-Z0-9_-]{0,31}$/u),
  version: leaveVersionDto,
  appliedRevision: leaveVersionDto,
});
export const leaveTypePageDto = z.object({
  items: z.array(leaveTypeDto).max(20),
  nextCursor: z.string().min(1).max(32).nullable(),
});
export const leavePolicyRevisionDto = terms.extend({
  revision: leaveVersionDto,
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
  recordedAt: leaveInstantDto,
});
export const leavePolicyReviewDto = z.object({
  current: leaveTypeDto,
  history: z.object({
    items: z.array(leavePolicyRevisionDto).max(20),
    nextCursor: z.string().min(1).max(16).nullable(),
  }),
});
export type LeaveTypeDto = z.infer<typeof leaveTypeDto>;
export type LeavePolicyTermsDto = z.infer<typeof terms>;
export type LeaveTypePageDto = z.infer<typeof leaveTypePageDto>;
export type LeavePolicyRevisionDto = z.infer<typeof leavePolicyRevisionDto>;
export type LeavePolicyReviewDto = z.infer<typeof leavePolicyReviewDto>;
