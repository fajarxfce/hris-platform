import { z } from "zod";
import { leaveVersionDto } from "./leave-value-dto";

const days = z
  .string()
  .max(5)
  .regex(/^(?:0|[1-9]\d{0,2})(?:\.5)?$/u)
  .refine((value) => Number(value) <= 366);
export const leaveCarryBalanceDto = z.object({
  closingId: z.uuid(),
  awardedDays: days,
  availableDays: days,
  reservedDays: days,
  consumedDays: days,
  expiredDays: days,
  transferredDays: days,
  deductedDays: days,
  validThrough: z.iso.date().nullable(),
  expiresAt: z.iso.datetime({ offset: true }).max(40).nullable(),
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
  version: leaveVersionDto,
});
export type LeaveCarryBalanceDto = z.infer<typeof leaveCarryBalanceDto>;
