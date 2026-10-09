import { z } from "zod";

export const jobDto = z.object({
  id: z.uuid(),
  kind: z.string().regex(/^[A-Z][A-Z0-9_]{0,79}$/u),
  status: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
  completedItems: z.number().int().min(0),
  totalItems: z.number().int().min(1),
  progressMode: z.enum(["FIXED_TOTAL", "UPPER_BOUND"]),
  attempts: z.number().int().min(0),
  cancellationRequested: z.boolean(),
  failureCode: z
    .string()
    .regex(/^[a-z][a-z0-9_]{0,99}$/u)
    .nullable(),
  createdAt: z.iso.datetime(),
  finishedAt: z.iso.datetime().nullable(),
  version: z.number().int().min(0),
  availableActions: z.array(z.string().regex(/^[a-z][a-z0-9_]{0,49}$/u)).max(16),
  scheduledFor: z.iso.datetime().nullable(),
  availableAt: z.iso.datetime(),
});
export type JobDto = z.infer<typeof jobDto>;
export const jobPageDto = z.object({
  items: z.array(jobDto).max(50),
  nextCreatedAt: z.iso.datetime().nullable(),
  nextId: z.uuid().nullable(),
});
export type JobPageDto = z.infer<typeof jobPageDto>;
