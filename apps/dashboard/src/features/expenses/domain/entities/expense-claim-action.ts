export const expenseClaimActions = ["SUBMIT", "CANCEL", "WITHDRAW"] as const;
export type ExpenseClaimAction = (typeof expenseClaimActions)[number];
