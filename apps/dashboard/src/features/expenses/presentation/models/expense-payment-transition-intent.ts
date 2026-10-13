import type { ExpensePaymentTransition } from "../../domain/entities/expense-payment-transition";
export const expensePaymentTransitionIntents = ["release", "cancel"] as const;
export type ExpensePaymentTransitionIntent = (typeof expensePaymentTransitionIntents)[number];
export const expensePaymentTransitionForIntent: Readonly<
  Record<ExpensePaymentTransitionIntent, ExpensePaymentTransition>
> = { release: "RELEASE", cancel: "CANCEL" };
