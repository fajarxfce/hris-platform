export const expensePaymentTransitions = ["RELEASE", "CANCEL"] as const;
export type ExpensePaymentTransition = (typeof expensePaymentTransitions)[number];
