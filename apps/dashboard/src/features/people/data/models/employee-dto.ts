import { z } from "zod";
import { employmentTermsDto } from "./employment-terms-dto";

export const employeeDto = z.object({
  id: z.uuid(),
  companyId: z.uuid(),
  employeeNumber: z.string().min(2).max(32),
  person: z.object({
    legalName: z.string().min(1).max(200),
    email: z.string().max(254).nullable(),
  }),
  terms: employmentTermsDto,
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  appliedRevision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type EmployeeDto = z.infer<typeof employeeDto>;

export const employeePageDto = z.object({
  items: z.array(employeeDto).max(50),
  nextCursor: z.string().max(32).nullable(),
});
export type EmployeePageDto = z.infer<typeof employeePageDto>;
