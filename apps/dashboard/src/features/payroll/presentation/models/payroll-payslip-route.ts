import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  PayrollPayslipScope,
  PayrollPayslipSearch,
} from "../../domain/entities/payroll-payslip";

export function payrollPayslipScopeFromRoute(
  parameters: URLSearchParams,
  companyReader: boolean,
): PayrollPayslipScope {
  return !companyReader || parameters.get("scope") === "SELF" ? "SELF" : "AUTHORIZED";
}
export function payrollPayslipSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
  companyReader: boolean,
): PayrollPayslipSearch {
  const replaced = parameters.has("company") && parameters.get("company") !== company;
  return {
    from: replaced
      ? `${today.slice(0, 4)}-01`
      : (parameters.get("from") ?? `${today.slice(0, 4)}-01`),
    until: replaced
      ? `${today.slice(0, 4)}-12`
      : (parameters.get("until") ?? `${today.slice(0, 4)}-12`),
    query: replaced ? "" : (parameters.get("query") ?? ""),
    employeeId: null,
    scope: replaced
      ? companyReader
        ? "AUTHORIZED"
        : "SELF"
      : payrollPayslipScopeFromRoute(parameters, companyReader),
    after: replaced ? null : parameters.get("after"),
  };
}
export function payrollPayslipParameters(
  company: CompanyId,
  search: PayrollPayslipSearch,
): URLSearchParams {
  const parameters = new URLSearchParams({
    company,
    from: search.from,
    until: search.until,
    scope: search.scope,
  });
  if (search.query) parameters.set("query", search.query);
  if (search.after) parameters.set("after", search.after);
  return parameters;
}
