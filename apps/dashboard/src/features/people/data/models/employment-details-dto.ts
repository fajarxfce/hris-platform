import { z } from "zod";
import { employeeDto } from "./employee-dto";
import { employmentTermsDto } from "./employment-terms-dto";

export const assignedEmploymentTermsDto = employmentTermsDto.extend({
  branchId: z.uuid().nullable(),
  departmentId: z.uuid().nullable(),
  positionId: z.uuid().nullable(),
  costCenterId: z.uuid().nullable(),
  managerId: z.uuid().nullable(),
});
const unitReferenceDto = z.object({
  id: z.uuid(),
  code: z.string().min(2).max(32),
  name: z.string().min(1).max(200),
  active: z.boolean(),
});
export const employmentDetailsDto = z.object({
  asOf: z.string().min(10).max(10),
  employee: employeeDto.extend({ terms: assignedEmploymentTermsDto }),
  branch: unitReferenceDto.nullable(),
  department: unitReferenceDto.nullable(),
  position: unitReferenceDto.nullable(),
  costCenter: unitReferenceDto.nullable(),
  manager: z
    .object({
      id: z.uuid(),
      employeeNumber: z.string().min(2).max(32),
      legalName: z.string().min(1).max(200),
      working: z.boolean(),
    })
    .nullable(),
});
export type EmploymentDetailsDto = z.infer<typeof employmentDetailsDto>;
export type AssignedEmploymentTermsDto = z.infer<typeof assignedEmploymentTermsDto>;
