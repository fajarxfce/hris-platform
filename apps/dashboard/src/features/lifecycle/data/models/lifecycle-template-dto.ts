import { z } from "zod";

export const lifecycleTemplateDto = z.object({
  id: z.uuid(),
  code: z.string().min(2).max(32),
  name: z.string().min(1).max(120),
  kind: z.enum(["ONBOARDING", "OFFBOARDING"]),
  active: z.boolean(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  tasks: z
    .array(
      z.object({
        key: z.string().min(1).max(48),
        title: z.string().min(1).max(160),
        required: z.boolean(),
        dueDays: z.number().int().min(-90).max(365),
      }),
    )
    .min(1)
    .max(64),
});
export type LifecycleTemplateDto = z.infer<typeof lifecycleTemplateDto>;
export const lifecycleTemplatePageDto = z.object({
  items: z.array(lifecycleTemplateDto).max(20),
  nextCursor: z.string().min(2).max(32).nullable(),
});
export type LifecycleTemplatePageDto = z.infer<typeof lifecycleTemplatePageDto>;
