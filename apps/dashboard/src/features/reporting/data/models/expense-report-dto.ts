import { z } from "zod";
import { expenseClaimStatuses } from "../../../expenses/domain/entities/expense-claim";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const amount = z
  .string()
  .max(34)
  .regex(/^(?:0|[1-9]\d{0,30})(?:\.\d{1,2})?$/u);
const metadata = {
  companyId: z.uuid(),
  from: z.iso.date(),
  until: z.iso.date(),
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("expenses.v1"),
  currency: z.literal("IDR"),
};
export const expenseReportCountsDto = z.object({
  claims: count,
  employments: count,
  lines: count,
  amount,
});
export const expenseReportDto = z.object({
  ...metadata,
  totals: expenseReportCountsDto,
  statuses: z
    .array(z.object({ status: z.enum(expenseClaimStatuses), counts: expenseReportCountsDto }))
    .length(6),
});
export const expenseReportClaimPageDto = z.object({
  ...metadata,
  status: z.enum(expenseClaimStatuses).nullable(),
  items: z
    .array(
      z.object({
        id: z.uuid(),
        submissionId: z.uuid(),
        submissionNumber: z.number().int().min(1).max(20),
        employeeId: z.uuid(),
        employeeNumber: z.string().min(1).max(32),
        employeeName: z.string().min(1).max(200),
        title: z.string().min(1).max(160),
        status: z.enum(expenseClaimStatuses),
        submittedAt: z.iso.datetime(),
        version: count,
        lines: z.number().int().min(1).max(20),
        amount,
        submissionAmount: amount,
      }),
    )
    .max(50),
  nextCursor: z.string().min(1).max(200).nullable(),
});
export type ExpenseReportDto = z.infer<typeof expenseReportDto>;
export type ExpenseReportCountsDto = z.infer<typeof expenseReportCountsDto>;
export type ExpenseReportClaimPageDto = z.infer<typeof expenseReportClaimPageDto>;
