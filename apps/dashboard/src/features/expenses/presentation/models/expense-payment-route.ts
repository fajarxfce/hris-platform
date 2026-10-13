import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpensePaymentSearchInput } from "../../domain/entities/expense-payment-search";
export function expensePaymentSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): ExpensePaymentSearchInput {
  return Object.freeze({
    from: parameters.get("from") ?? `${today.slice(0, 8)}01`,
    until: parameters.get("until") ?? today,
    status: parameters.get("status"),
    after: parameters.get("company") === company ? parameters.get("after") : null,
  });
}
export function expensePaymentParameters(
  company: CompanyId,
  search: ExpensePaymentSearchInput,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, from: search.from, until: search.until });
  if (search.status !== null) parameters.set("status", search.status);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
export const expensePaymentPath = (id: string): string =>
  `/expenses/payments/${encodeURIComponent(id)}`;
export const expensePaymentTabs = ["overview", "instructions", "results", "history"] as const;
export type ExpensePaymentTab = (typeof expensePaymentTabs)[number];
export const expensePaymentTab = (value: string | null): ExpensePaymentTab =>
  expensePaymentTabs.find((tab) => tab === value) ?? "overview";
