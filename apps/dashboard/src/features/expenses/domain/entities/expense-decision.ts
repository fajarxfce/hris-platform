export const expenseDecisions = ["APPROVE", "REJECT", "RETURN"] as const;
export type ExpenseDecision = (typeof expenseDecisions)[number];
