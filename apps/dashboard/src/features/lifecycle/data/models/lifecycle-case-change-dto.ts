import { z } from "zod";

export type LifecycleCaseChangeDto = Readonly<{
  expectedVersion: number;
  reason: string;
}>;
export const lifecycleCaseReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
});
export type LifecycleCaseReceiptDto = z.infer<typeof lifecycleCaseReceiptDto>;
