import { z } from "zod";

export type DocumentMutationDto = Readonly<{ expectedVersion: number; reason: string }>;
export const documentMutationReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
});
export type DocumentMutationReceiptDto = z.infer<typeof documentMutationReceiptDto>;
