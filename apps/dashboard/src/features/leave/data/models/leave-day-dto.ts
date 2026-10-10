import { z } from "zod";
import { leavePortions } from "../../domain/entities/leave-day";
import { leaveDateDto, leaveInstantDto, leaveVersionDto } from "./leave-value-dto";

export const leaveDayDto = z.object({
  workDate: leaveDateDto,
  portion: z.enum(leavePortions),
  chargedDays: z.enum(["1", "0.5"]),
  startsAt: leaveInstantDto,
  endsAt: leaveInstantDto,
  plannedMinutes: z.number().int().min(1).max(2880),
  chargedMinutes: z.number().int().min(1).max(2880),
  shiftId: z.uuid(),
  shiftRevision: leaveVersionDto,
  scheduleOrigin: z.enum(["PATTERN", "ROSTER", "HOLIDAY"]),
  scheduleRevision: leaveVersionDto,
});
export type LeaveDayDto = z.infer<typeof leaveDayDto>;
