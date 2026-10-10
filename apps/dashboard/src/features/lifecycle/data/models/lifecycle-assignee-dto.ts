import { z } from "zod";

export const lifecycleAssigneePageDto = z.object({
  items: z.array(z.object({ id: z.uuid(), displayName: z.string().min(1).max(200) })).max(50),
  nextCursor: z.uuid().nullable(),
});
export type LifecycleAssigneePageDto = z.infer<typeof lifecycleAssigneePageDto>;
