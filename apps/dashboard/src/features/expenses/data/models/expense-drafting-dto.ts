import { z } from "zod";
import { expenseInstantDto, expenseVersionDto } from "./expense-claim-dto";

const employee = z.object({
  id: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  legalName: z.string().min(1).max(200),
  status: z.enum(["PROBATION", "ACTIVE", "SUSPENDED", "ENDED"]),
  version: expenseVersionDto,
});
export const expenseDraftDirectoryDto = z.object({
  companyDate: z.iso.date(),
  timezone: z.string().min(1).max(128),
  employees: z.object({
    items: z.array(employee).max(20),
    nextCursor: z.string().min(1).max(32).nullable(),
  }),
});
const mediaType = z.enum(["application/pdf", "image/png", "image/jpeg"]);
const fileName = z
  .string()
  .min(1)
  .max(180)
  .regex(/^[^\p{Cc}/\\]+$/u);
const fileSize = z
  .number()
  .int()
  .min(1)
  .max(100 * 1024 * 1024);
export const expenseDraftReceiptPageDto = z.object({
  items: z
    .array(
      z.object({
        documentId: z.uuid(),
        revisionId: z.uuid(),
        revisionNumber: z.number().int().min(1).max(100),
        title: z.string().min(1).max(160),
        fileName,
        mediaType,
        size: fileSize,
        validatedAt: expenseInstantDto,
      }),
    )
    .max(20),
  nextCursor: z.uuid().nullable(),
});
const label = z.object({
  id: z.uuid(),
  code: z.string().min(1).max(32).nullable(),
  name: z.string().min(1).max(200).nullable(),
  active: z.boolean().nullable(),
});
export const expenseDraftReferencesDto = z.object({
  employmentId: z.uuid(),
  categories: z.array(label).max(20),
  costCenters: z.array(label).max(20),
  receipts: z
    .array(
      z.object({
        revisionId: z.uuid(),
        documentId: z.uuid().nullable(),
        fileName: fileName.nullable(),
        mediaType: mediaType.nullable(),
        size: fileSize.nullable(),
        status: z
          .enum([
            "UPLOADING",
            "VALIDATING",
            "VALIDATION_FAILED",
            "READY",
            "REJECTED",
            "CANCELLED",
            "EXPIRED",
            "RETIRED",
          ])
          .nullable(),
      }),
    )
    .max(60),
});
export const expenseCostCenterPageDto = z.object({
  items: z
    .array(
      z.object({
        id: z.uuid(),
        code: z.string().regex(/^[A-Z0-9][A-Z0-9_-]{1,31}$/u),
        name: z.string().min(1).max(200),
        version: expenseVersionDto,
      }),
    )
    .max(20),
  nextCursor: z
    .string()
    .regex(/^COST_CENTER:[A-Z0-9][A-Z0-9_-]{1,31}$/u)
    .nullable(),
});
export type ExpenseDraftDirectoryDto = z.infer<typeof expenseDraftDirectoryDto>;
export type ExpenseDraftReceiptPageDto = z.infer<typeof expenseDraftReceiptPageDto>;
export type ExpenseDraftReferencesDto = z.infer<typeof expenseDraftReferencesDto>;
export type ExpenseCostCenterPageDto = z.infer<typeof expenseCostCenterPageDto>;
