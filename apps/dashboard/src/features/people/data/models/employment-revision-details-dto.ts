import { z } from "zod";
import { employmentRevisionDto } from "./employment-revision-dto";

export const employmentRevisionDetailsDto = z.object({
  employeeId: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  companyDate: z.iso.date(),
  revision: employmentRevisionDto,
  canCancel: z.boolean(),
});
export type EmploymentRevisionDetailsDto = z.infer<typeof employmentRevisionDetailsDto>;
