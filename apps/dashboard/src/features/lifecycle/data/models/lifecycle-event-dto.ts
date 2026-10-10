import { z } from "zod";

export const lifecycleEventDto = z.object({
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  taskKey: z.string().min(1).max(48).nullable(),
  action: z.enum([
    "CREATED",
    "ASSIGNED",
    "TASK_PENDING",
    "TASK_DONE",
    "TASK_WAIVED",
    "CANCELLED",
    "COMPLETED",
  ]),
  assigneeId: z.uuid().nullable(),
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
  recordedAt: z.string().min(1).max(40),
});
export const lifecycleHistoryPageDto = z.object({
  items: z.array(lifecycleEventDto).max(50),
  nextCursor: z.string().min(1).max(16).nullable(),
});
export type LifecycleHistoryPageDto = z.infer<typeof lifecycleHistoryPageDto>;
