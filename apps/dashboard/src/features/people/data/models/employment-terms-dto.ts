import { z } from "zod";

export const employmentTermsDto = z.object({
  effectiveFrom: z.iso.date(),
  contract: z.enum(["PERMANENT", "FIXED_TERM"]),
  startDate: z.iso.date(),
  endDate: z.iso.date().nullable(),
  status: z.enum(["PROBATION", "ACTIVE", "SUSPENDED", "ENDED"]),
});
export type EmploymentTermsDto = z.infer<typeof employmentTermsDto>;
