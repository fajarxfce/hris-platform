import { z } from "zod";
import { assignedEmploymentTermsDto } from "./employment-details-dto";

const rowStatus = z.enum(["PENDING", "READY", "INVALID", "APPLIED", "REJECTED"]);
export const employeeImportDto = z.object({
  id: z.uuid(),
  fileName: z.string().min(1).max(120),
  sourceHash: z.string().regex(/^[0-9a-f]{64}$/u),
  rowCount: z.number().int().min(1).max(5000),
  status: z.enum(["PREVIEWING", "REVIEW", "IMPORTING", "COMPLETED", "STOPPED", "CANCELLED"]),
  jobId: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  createdBy: z.uuid(),
  createdAt: z.string().max(40),
  reason: z.string().min(1).max(1000),
});
export const employeeImportPageDto = z.object({
  items: z.array(employeeImportDto).max(10),
  nextCursor: z.uuid().nullable(),
});
export const employeeImportSummaryDto = z.object({
  batch: employeeImportDto,
  counts: z.partialRecord(rowStatus, z.number().int().min(0).max(5000)),
});
const proposalDate = z.string().regex(/^[+-]?\d{4,9}-\d{2}-\d{2}$/u);
const proposalDto = z.object({
  employeeId: z.uuid(),
  employeeNumber: z.string().max(1024),
  legalName: z.string().max(1024),
  birthDate: proposalDate.nullable(),
  nationality: z.string().max(1024),
  email: z.string().max(1024).nullable(),
  terms: assignedEmploymentTermsDto.extend({
    effectiveFrom: proposalDate,
    startDate: proposalDate,
    endDate: proposalDate.nullable(),
  }),
});
export const employeeImportRowDto = z.object({
  number: z.number().int().min(1).max(5000),
  employeeNumber: z.string().max(1024),
  legalName: z.string().max(1024),
  status: rowStatus,
  issues: z
    .record(z.string().max(100), z.string().regex(/^[a-z][a-z0-9_]{0,99}$/u))
    .refine((issues) => Object.keys(issues).length <= 32),
  proposed: proposalDto.nullable(),
  createdEmploymentId: z.uuid().nullable(),
});
export const employeeImportRowsDto = z.object({
  items: z.array(employeeImportRowDto).max(25),
  nextCursor: z
    .string()
    .regex(/^[1-9]\d{0,3}$/u)
    .nullable(),
});
export const employeeImportAttemptDto = z.object({
  jobId: z.uuid(),
  phase: z.enum(["PREVIEW", "APPLY"]),
  actorId: z.uuid(),
  createdAt: z.string().max(40),
});
export const employeeImportAttemptsDto = z.object({
  items: z.array(employeeImportAttemptDto).max(10),
  nextCursor: z.uuid().nullable(),
});
export type EmployeeImportDto = z.infer<typeof employeeImportDto>;
export type EmployeeImportPageDto = z.infer<typeof employeeImportPageDto>;
export type EmployeeImportSummaryDto = z.infer<typeof employeeImportSummaryDto>;
export type EmployeeImportRowDto = z.infer<typeof employeeImportRowDto>;
export type EmployeeImportRowsDto = z.infer<typeof employeeImportRowsDto>;
export type EmployeeImportAttemptsDto = z.infer<typeof employeeImportAttemptsDto>;
