import { z } from "zod";
import type { ExpenseDecision } from "../../domain/entities/expense-decision";
import { expenseVersionDto } from "./expense-claim-dto";
export type ExpenseDecisionDto = {
  expectedVersion: number;
  expectedApprovalVersion: number;
  decision: ExpenseDecision;
  reason: string;
  acknowledgeDuplicates: boolean;
};
export const expenseDecisionReceiptDto = z.object({ id: z.uuid(), version: expenseVersionDto });
export type ExpenseDecisionReceiptDto = z.infer<typeof expenseDecisionReceiptDto>;
