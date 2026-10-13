import type { ExpenseDecision } from "../../domain/entities/expense-decision";
export type ExpenseDecisionDto = {
  expectedVersion: number;
  expectedApprovalVersion: number;
  decision: ExpenseDecision;
  reason: string;
  acknowledgeDuplicates: boolean;
};
