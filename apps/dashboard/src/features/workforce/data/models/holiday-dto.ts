import { z } from "zod";

export const holidayDto = z.object({
  id: z.uuid(),
  workDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  name: z.string().min(1).max(200),
  active: z.boolean(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export const holidaysDto = z.array(holidayDto).max(366);
export const holidayReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type HolidayDto = z.infer<typeof holidayDto>;
export type HolidayReceiptDto = z.infer<typeof holidayReceiptDto>;
export type HolidayRangeDto = Readonly<{ from: string; until: string }>;
export type HolidayChangeDto = Readonly<{
  workDate: string;
  name: string;
  active: boolean;
  expectedVersion: number | null;
  reason: string;
}>;
