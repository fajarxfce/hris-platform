import { z } from "zod";

export type WorkPeriodCommandDto = Readonly<{ expectedVersion: number; reason: string }>;
export type WorkPeriodEmployeeSearchDto = Readonly<{
  month: string;
  query: string;
  after: string | null;
}>;
export const workPeriodReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type WorkPeriodReceiptDto = z.infer<typeof workPeriodReceiptDto>;
