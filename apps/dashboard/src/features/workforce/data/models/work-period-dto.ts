import { z } from "zod";
import { workPeriodStatuses } from "../../domain/entities/work-period";

export const workPeriodDto = z.object({
  id: z.uuid(),
  month: z.string().regex(/^\d{4}-\d{2}$/u),
  status: z.enum(workPeriodStatuses),
  timezone: z.string().min(1).max(100).nullable(),
  jobId: z.uuid().nullable(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  startedAt: z.string().max(40).nullable(),
  closedAt: z.string().max(40).nullable(),
  failureCode: z.string().min(1).max(80).nullable(),
});
export type WorkPeriodDto = z.infer<typeof workPeriodDto>;
