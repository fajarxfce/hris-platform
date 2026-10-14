import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollPaymentSearchInput } from "../../domain/entities/payroll-payment-search";
export function payrollPaymentSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): PayrollPaymentSearchInput {
  return Object.freeze({
    from: parameters.get("from") ?? `${today.slice(0, 8)}01`,
    until: parameters.get("until") ?? today,
    status: parameters.get("status"),
    after: parameters.get("company") === company ? parameters.get("after") : null,
  });
}
export function payrollPaymentParameters(
  company: CompanyId,
  search: PayrollPaymentSearchInput,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, from: search.from, until: search.until });
  if (search.status !== null) parameters.set("status", search.status);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
export const payrollPaymentPath = (id: string): string =>
  `/payroll/payments/${encodeURIComponent(id)}`;
export const payrollPaymentTabs = ["overview", "instructions", "results", "history"] as const;
export type PayrollPaymentTab = (typeof payrollPaymentTabs)[number];
export const payrollPaymentTab = (value: string | null): PayrollPaymentTab =>
  payrollPaymentTabs.find((tab) => tab === value) ?? "overview";
