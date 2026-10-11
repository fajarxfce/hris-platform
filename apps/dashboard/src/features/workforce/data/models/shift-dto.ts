import { z } from "zod";

const minuteTime = z.string().regex(/^(?:[01]\d|2[0-3]):[0-5]\d(?::00(?:\.0{1,9})?)?$/u);
export const shiftDetailsDto = z.object({
  code: z.string().min(2).max(32),
  name: z.string().min(1).max(200),
  startsAt: minuteTime,
  endsAt: minuteTime,
  breakMinutes: z.number().int().min(0).max(1439),
  timezone: z.string().min(1).max(128),
  mode: z.enum(["ONSITE", "REMOTE", "FIELD"]),
  locationRequired: z.boolean(),
  maxAccuracyMeters: z.number().min(1).max(10_000),
  fence: z
    .object({
      latitude: z.number().min(-90).max(90),
      longitude: z.number().min(-180).max(180),
      radiusMeters: z.number().min(1).max(100_000),
    })
    .nullable(),
});
export const shiftDto = z.object({
  id: z.uuid(),
  details: shiftDetailsDto,
  active: z.boolean(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export const shiftPageDto = z.object({
  items: z.array(shiftDto).max(50),
  nextCursor: z.string().min(2).max(32).nullable(),
});
export const shiftReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type ShiftDto = z.infer<typeof shiftDto>;
export type ShiftDetailsDto = z.infer<typeof shiftDetailsDto>;
export type ShiftPageDto = z.infer<typeof shiftPageDto>;
export type ShiftReceiptDto = z.infer<typeof shiftReceiptDto>;
export type ShiftSearchDto = Readonly<{ query: string; after: string | null }>;
export type ShiftChangeDto = z.infer<typeof shiftDetailsDto> &
  Readonly<{
    active: boolean;
    expectedVersion: number | null;
    reason: string;
  }>;
