import { z } from "zod";
import { employmentTermsDto } from "./employment-terms-dto";

export const employmentRevisionDto = z.object({
  revision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  terms: employmentTermsDto,
  reason: z.string().min(1).max(1000),
  recordedAt: z.iso.datetime(),
  cancellation: z
    .object({ reason: z.string().min(1).max(1000), recordedAt: z.iso.datetime() })
    .nullable(),
});
export type EmploymentRevisionDto = z.infer<typeof employmentRevisionDto>;
export const employmentHistoryPageDto = z.object({
  items: z.array(employmentRevisionDto).max(50),
  nextCursor: z.string().max(16).nullable(),
});
export type EmploymentHistoryPageDto = z.infer<typeof employmentHistoryPageDto>;
