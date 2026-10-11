import { z } from "zod";
import { employeeDto } from "./employee-dto";
import type { AssignedEmploymentTermsDto } from "./employment-details-dto";

const companyDto = z.object({
  id: z.uuid(),
  code: z.string().min(2).max(32),
  name: z.string().min(1).max(200),
  timezone: z.string().min(1).max(128),
});
export const employmentTransferReviewDto = z.object({
  employee: employeeDto,
  sourceCompany: companyDto,
  targetCompany: companyDto,
  sourceDate: z.iso.date(),
  targetDate: z.iso.date(),
  evaluatedAt: z.iso.datetime({ offset: true }),
  targetPermissions: z
    .array(
      z
        .string()
        .regex(/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/u)
        .max(128),
    )
    .max(256),
});
export type EmploymentTransferReviewDto = z.infer<typeof employmentTransferReviewDto>;

export const employmentTransferDto = z.object({
  id: z.uuid(),
  sourceCompanyId: z.uuid(),
  sourceEmploymentId: z.uuid(),
  targetCompanyId: z.uuid(),
  targetEmploymentId: z.uuid(),
  sourceVersion: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
  effectiveDate: z.iso.date(),
  reason: z.string().min(1).max(1000),
  recordedAt: z.iso.datetime({ offset: true }),
});
// An employment can be received once and transferred once; the API retains both immutable records.
export const employmentTransfersDto = z.array(employmentTransferDto).max(2);
export type EmploymentTransferDto = z.infer<typeof employmentTransferDto>;
export type EmploymentTransferInputDto = {
  targetCompanyId: string;
  targetEmploymentId: string;
  expectedVersion: number;
  employeeNumber: string;
  terms: AssignedEmploymentTermsDto;
  reason: string;
};
