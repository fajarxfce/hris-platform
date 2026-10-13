import { z } from "zod";

const version = z.number().int().min(0).max(999);
const amount = z.string().regex(/^(?:0|[1-9]\d{0,11})(?:\.\d{1,2})?$/u);
const code = z.string().regex(/^[A-Z][A-Z0-9_]{1,31}$/u);
export const expenseCategoryDto = z.object({
  id: z.uuid(),
  code,
  name: z.string().min(1).max(120),
  currency: z.literal("IDR"),
  effectiveFrom: z.string().length(10),
  maximumLineAmount: amount,
  maximumClaimAmount: amount,
  receiptRequired: z.boolean(),
  costCenterRequired: z.boolean(),
  maximumAgeDays: z.number().int().min(1).max(366),
  allowedContracts: z
    .array(z.enum(["PERMANENT", "FIXED_TERM"]))
    .min(1)
    .max(2),
  active: z.boolean(),
  version,
  appliedRevision: version,
});
export const expenseCategoryPageDto = z.object({
  items: z.array(expenseCategoryDto).max(20),
  nextCursor: code.nullable(),
});
export const expensePolicyReviewDto = z.object({
  current: expenseCategoryDto,
  history: z.object({
    items: z
      .array(
        z.object({
          category: expenseCategoryDto,
          actorId: z.uuid(),
          reason: z.string().min(1).max(1000),
          recordedAt: z.iso.datetime({ offset: true }),
        }),
      )
      .max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
});
export const expenseReceiptDto = z.object({ id: z.uuid(), version });
export type ExpenseCategoryDto = z.infer<typeof expenseCategoryDto>;
export type ExpenseCategoryPageDto = z.infer<typeof expenseCategoryPageDto>;
export type ExpensePolicyReviewDto = z.infer<typeof expensePolicyReviewDto>;
export type ExpenseReceiptDto = z.infer<typeof expenseReceiptDto>;
