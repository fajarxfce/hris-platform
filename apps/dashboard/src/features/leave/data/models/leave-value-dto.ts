import { z } from "zod";

export const leaveVersionDto = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const leaveDayQuantityDto = z
  .string()
  .max(5)
  .regex(/^(?:0|[1-9]\d{0,2})(?:\.5)?$/u)
  .refine((value) => Number(value) > 0 && Number(value) <= 366);
export const leaveInstantDto = z.string().min(20).max(40);
export const leaveDateDto = z.string().length(10);
