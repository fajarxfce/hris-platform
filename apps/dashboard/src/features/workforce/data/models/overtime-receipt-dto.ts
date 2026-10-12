import { z } from "zod";

export const overtimeReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(999),
});
export type OvertimeReceiptDto = z.infer<typeof overtimeReceiptDto>;
