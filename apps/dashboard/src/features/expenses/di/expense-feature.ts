import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpExpensePolicyDataSource } from "../data/datasources/http-expense-policy-data-source";
import { RemoteExpensePolicyRepository } from "../data/repositories/remote-expense-policy-repository";
import { LoadExpensePolicies } from "../domain/usecases/load-expense-policies";
import { LoadExpensePolicy } from "../domain/usecases/load-expense-policy";
import { SaveExpensePolicy } from "../domain/usecases/save-expense-policy";
import type { ExpenseUseCases } from "../presentation/contracts/expense-use-cases";

export function createExpenseFeature(http: HttpClient): ExpenseUseCases {
  const policies = new RemoteExpensePolicyRepository(new HttpExpensePolicyDataSource(http));
  return {
    loadPolicies: new LoadExpensePolicies(policies),
    loadPolicy: new LoadExpensePolicy(policies),
    savePolicy: new SaveExpensePolicy(policies),
  };
}
