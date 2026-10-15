import { z } from "zod";
export const lifecycleScheduleJobDto = z.object({
  id: z.uuid(),
  status: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  attempts: z.number().int().min(0).max(8),
  cancellationRequested: z.boolean(),
  completedItems: z.number().int().min(0).max(1),
  failureCode: z
    .string()
    .regex(/^[a-z][a-z0-9_.]{0,79}$/u)
    .nullable(),
  finishedAt: z.iso.datetime({ offset: true }).nullable(),
});
