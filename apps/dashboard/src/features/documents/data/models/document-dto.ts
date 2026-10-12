import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const instant = z.iso.datetime({ offset: true }).max(40);
const employeeNumber = z.string().regex(/^[A-Z0-9][A-Z0-9_-]{1,31}$/u);
export const documentEmployeeDto = z.object({
  id: z.uuid(),
  employeeNumber,
  name: z.string().min(1).max(200),
  status: z.enum(["PROBATION", "ACTIVE", "SUSPENDED", "ENDED"]),
  version,
});
export const documentEmployeeDirectoryDto = z.object({
  companyId: z.uuid(),
  asOf: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  timezone: z.string().min(1).max(100),
  query: z.string().max(120),
  employees: z.object({
    items: z.array(documentEmployeeDto).max(20),
    nextCursor: employeeNumber.nullable(),
  }),
});
export const documentDto = z.object({
  id: z.uuid(),
  employmentId: z.uuid(),
  title: z.string().min(1).max(160),
  classification: z.enum(["PERSONAL", "RECEIPT", "HR_ONLY"]),
  revisionCount: z.number().int().min(1).max(100),
  version,
  createdBy: z.uuid(),
  createdAt: instant,
  currentRevisionId: z.uuid().nullable(),
});
export const documentPageDto = z.object({
  items: z.array(documentDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const documentRevisionDto = z.object({
  id: z.uuid(),
  documentId: z.uuid(),
  number: z.number().int().min(1).max(100),
  fileName: z
    .string()
    .min(1)
    .max(180)
    .regex(/^[^\p{Cc}/\\]+$/u),
  mediaType: z.enum(["application/pdf", "image/jpeg", "image/png"]),
  size: z
    .number()
    .int()
    .min(1)
    .max(100 * 1024 * 1024),
  sha256: z.string().regex(/^[0-9a-f]{64}$/u),
  status: z.enum([
    "UPLOADING",
    "VALIDATING",
    "VALIDATION_FAILED",
    "READY",
    "REJECTED",
    "CANCELLED",
    "EXPIRED",
    "RETIRED",
  ]),
  uploadedBytes: z
    .number()
    .int()
    .min(0)
    .max(100 * 1024 * 1024),
  chunkSize: z.literal(1024 * 1024),
  version,
  createdBy: z.uuid(),
  createdAt: instant,
  expiresAt: instant,
  reason: z.string().min(1).max(1000),
  validationJobId: z.uuid().nullable(),
  validationAttempts: z.number().int().min(0).max(8),
  failureCode: z
    .string()
    .regex(/^[a-z][a-z0-9_.]{0,119}$/u)
    .nullable(),
  validatedAt: instant.nullable(),
  availableActions: z
    .array(z.string().regex(/^[A-Z][A-Z_]{0,47}$/u))
    .max(16)
    .default([]),
  uploadContinuation: z
    .object({
      operationId: z.uuid(),
      offset: z
        .number()
        .int()
        .min(0)
        .max(100 * 1024 * 1024),
      size: z
        .number()
        .int()
        .min(1)
        .max(1024 * 1024),
      sha256: z.string().regex(/^[0-9a-f]{64}$/u),
      attempts: z.number().int().min(0).max(7),
      retryAt: instant.nullable(),
    })
    .nullable()
    .default(null),
});
export const documentRevisionPageDto = z.object({
  items: z.array(documentRevisionDto).max(20),
  nextCursor: z
    .string()
    .regex(/^[1-9]\d{0,2}$/u)
    .nullable(),
});
export const documentValidationAttemptDto = z.object({
  jobId: z.uuid(),
  number: z.number().int().min(1).max(8),
  actorId: z.uuid(),
  createdAt: instant,
  reason: z.string().min(1).max(1000),
});
export const documentValidationAttemptsDto = z.array(documentValidationAttemptDto).max(8);
export type DocumentDto = z.infer<typeof documentDto>;
export type DocumentPageDto = z.infer<typeof documentPageDto>;
export type DocumentEmployeeDirectoryDto = z.infer<typeof documentEmployeeDirectoryDto>;
export type DocumentRevisionDto = z.infer<typeof documentRevisionDto>;
export type DocumentRevisionPageDto = z.infer<typeof documentRevisionPageDto>;
export type DocumentValidationAttemptDto = z.infer<typeof documentValidationAttemptDto>;
