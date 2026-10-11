import { z } from "zod";
export const communicationsReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
});
export type CommunicationsReceiptDto = z.infer<typeof communicationsReceiptDto>;
