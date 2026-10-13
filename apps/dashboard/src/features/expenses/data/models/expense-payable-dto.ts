import { z } from "zod";
import { expensePayableActions } from "../../domain/entities/expense-payable";
import { expenseAmountDto, expenseInstantDto } from "./expense-claim-dto";
export const expensePayableDto = z.object({
  claimId: z.uuid(),
  submissionId: z.uuid(),
  employmentId: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  title: z.string().min(1).max(160),
  amount: expenseAmountDto,
  currency: z.literal("IDR"),
  approvedAt: expenseInstantDto,
  availableActions: z.array(z.enum(expensePayableActions)).max(1),
});
export const expensePayablePageDto = z.object({
  items: z.array(expensePayableDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export type ExpensePayableDto = z.infer<typeof expensePayableDto>;
export type ExpensePayablePageDto = z.infer<typeof expensePayablePageDto>;
