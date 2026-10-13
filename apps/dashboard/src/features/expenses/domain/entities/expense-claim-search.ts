import type { ExpenseClaimStatus } from "./expense-claim";
export type ExpenseClaimSearchInput = Readonly<{
  from: string;
  until: string;
  status: string | null;
  employmentId: string | null;
  after: string | null;
}>;
export type ExpenseClaimSearch = Omit<ExpenseClaimSearchInput, "status"> &
  Readonly<{ status: ExpenseClaimStatus | null }>;
