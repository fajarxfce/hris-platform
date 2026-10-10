import { z } from "zod";

export type LifecycleTaskChangeDto = Readonly<{
  expectedVersion: number;
  status: "PENDING" | "DONE" | "WAIVED";
  reason: string;
}>;
export const lifecycleTaskReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
});
export type LifecycleTaskReceiptDto = z.infer<typeof lifecycleTaskReceiptDto>;
