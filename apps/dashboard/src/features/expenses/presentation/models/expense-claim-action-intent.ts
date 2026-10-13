import type { ExpenseClaimAction } from "../../domain/entities/expense-claim-action";
export const expenseClaimActionForIntent = {
  submit: "SUBMIT",
  cancel: "CANCEL",
  withdraw: "WITHDRAW",
} as const;
export type ExpenseClaimActionIntent = keyof typeof expenseClaimActionForIntent;
export const expenseClaimIntentForAction: Readonly<
  Record<ExpenseClaimAction, ExpenseClaimActionIntent>
> = { SUBMIT: "submit", CANCEL: "cancel", WITHDRAW: "withdraw" };
