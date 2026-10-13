import type { LoadExpensePolicies } from "../../domain/usecases/load-expense-policies";
import type { LoadExpensePolicy } from "../../domain/usecases/load-expense-policy";
import type { SaveExpensePolicy } from "../../domain/usecases/save-expense-policy";

export type ExpenseUseCases = Readonly<{
  loadPolicies: Pick<LoadExpensePolicies, "execute">;
  loadPolicy: Pick<LoadExpensePolicy, "execute">;
  savePolicy: Pick<SaveExpensePolicy, "execute">;
}>;
