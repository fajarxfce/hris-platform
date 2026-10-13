import type { ExpenseCategory } from "../../domain/entities/expense-category";
import type { ExpenseCostCenter } from "../../domain/entities/expense-cost-center";
import type { ExpenseDraftReceiptOption } from "../../domain/entities/expense-draft-receipt-option";
export type ExpenseDraftOption =
  | Readonly<{ kind: "category"; id: string; value: ExpenseCategory }>
  | Readonly<{ kind: "costCenter"; id: string; value: ExpenseCostCenter }>
  | Readonly<{ kind: "receipt"; id: string; value: ExpenseDraftReceiptOption }>;
export type ExpenseDraftOptionPage = Readonly<{
  items: readonly ExpenseDraftOption[];
  nextCursor: string | null;
}>;
