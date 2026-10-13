import { z } from "zod";
import { leaveBalanceQuantityDto } from "./leave-balance-dto";
import { leaveDayDto } from "./leave-day-dto";
import { leavePolicyDto } from "./leave-policy-dto";
import {
  leaveDateDto,
  leaveDayQuantityDto,
  leaveInstantDto,
  leaveVersionDto,
} from "./leave-value-dto";

export const leaveRequestPreviewDto = z.object({
  employeeId: z.uuid(),
  evaluatedAt: leaveInstantDto,
  timezone: z.string().min(1).max(100),
  policy: leavePolicyDto,
  days: z.array(leaveDayDto).min(1).max(366),
  chargedDays: leaveDayQuantityDto,
  excludedDates: z.array(leaveDateDto).max(366),
  balances: z
    .array(
      z.object({
        year: z.number().int().min(1900).max(2200),
        availableDays: leaveBalanceQuantityDto,
        reservedDays: leaveBalanceQuantityDto,
        consumedDays: leaveBalanceQuantityDto,
        requestedDays: leaveDayQuantityDto,
        remainingDays: leaveBalanceQuantityDto,
        version: leaveVersionDto,
      }),
    )
    .min(1)
    .max(2),
  revision: z.string().regex(/^[0-9a-f]{64}$/u),
});
export type LeaveRequestPreviewDto = z.infer<typeof leaveRequestPreviewDto>;
