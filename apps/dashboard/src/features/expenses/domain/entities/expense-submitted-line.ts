import type { ExpensePolicyTerms } from "./expense-policy-terms";
import type { ExpenseReceipt } from "./expense-receipt";
export type ExpenseSubmittedLine = Readonly<{
  id: string;
  occurredOn: string;
  amount: string;
  description: string;
  category: ExpensePolicyTerms &
    Readonly<{ id: string; code: string; effectiveFrom: string; revision: number }>;
  costCenter: Readonly<{ id: string; code: string; name: string; version: number }> | null;
  receipts: readonly ExpenseReceipt[];
}>;
