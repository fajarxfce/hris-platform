import { z } from "zod";
import { leaveChangeKinds } from "../../domain/entities/leave-history";
import { leaveStatuses } from "../../domain/entities/leave-request";
import { leaveInstantDto, leaveVersionDto } from "./leave-value-dto";

export const leaveChangeDto = z.object({
  version: leaveVersionDto,
  kind: z.enum(leaveChangeKinds),
  status: z.enum(leaveStatuses),
  cancellationApprovalId: z.uuid().nullable(),
  actorId: z.uuid(),
  recordedAt: leaveInstantDto,
  reason: z.string().max(1000),
});
export const leaveHistoryDto = z.object({
  items: z.array(leaveChangeDto).max(20),
  nextCursor: z
    .string()
    .max(16)
    .regex(/^(0|[1-9]\d*)$/u)
    .nullable(),
});
export type LeaveHistoryDto = z.infer<typeof leaveHistoryDto>;
