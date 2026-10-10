import { z } from "zod";

export const leaveReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type LeaveReceiptDto = z.infer<typeof leaveReceiptDto>;
