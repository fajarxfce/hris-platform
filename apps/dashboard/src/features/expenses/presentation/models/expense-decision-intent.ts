import type { ExpenseDecision } from "../../domain/entities/expense-decision";
export const expenseDecisionIntents = ["approve", "reject", "return"] as const;
export type ExpenseDecisionIntent = (typeof expenseDecisionIntents)[number];
export const expenseDecisionForIntent: Readonly<Record<ExpenseDecisionIntent, ExpenseDecision>> = {
  approve: "APPROVE",
  reject: "REJECT",
  return: "RETURN",
};
