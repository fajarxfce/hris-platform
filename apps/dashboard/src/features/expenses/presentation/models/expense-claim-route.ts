import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimSearchInput } from "../../domain/entities/expense-claim-search";
export function expenseClaimSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): ExpenseClaimSearchInput {
  return Object.freeze({
    from: parameters.get("from") ?? `${today.slice(0, 8)}01`,
    until: parameters.get("until") ?? today,
    status: parameters.get("status"),
    employmentId: parameters.get("company") === company ? parameters.get("employmentId") : null,
    after: parameters.get("company") === company ? parameters.get("after") : null,
  });
}
export function expenseClaimParameters(
  company: CompanyId,
  search: ExpenseClaimSearchInput,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, from: search.from, until: search.until });
  if (search.status !== null) parameters.set("status", search.status);
  if (search.employmentId !== null) parameters.set("employmentId", search.employmentId);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
export const expenseClaimPath = (id: string): string =>
  `/expenses/claims/${encodeURIComponent(id)}`;
export const expenseSubmissionPath = (id: string): string =>
  `/expenses/submissions/${encodeURIComponent(id)}`;
export const expenseClaimTabs = ["overview", "submissions", "history", "payments"] as const;
export type ExpenseClaimTab = (typeof expenseClaimTabs)[number];
export const expenseClaimTab = (value: string | null): ExpenseClaimTab =>
  expenseClaimTabs.find((tab) => tab === value) ?? "overview";
