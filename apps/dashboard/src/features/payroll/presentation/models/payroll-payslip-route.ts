import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import type {
  PayrollPayslipScope,
  PayrollPayslipSearch,
} from "../../domain/entities/payroll-payslip";
import { validPayrollPayslipRevision } from "../../domain/policies/payroll-payslip-policy";

/** Keep malformed selections invalid so the use case cannot silently load the current revision. */
export function payrollPayslipRevisionFromRoute(parameters: URLSearchParams): number | null {
  const values = parameters.getAll("revision");
  if (values.length === 0) return null;
  const raw = values[0] ?? "",
    revision = Number(raw);
  return values.length === 1 &&
    /^(?:0|[1-9]\d{0,15})$/u.test(raw) &&
    validPayrollPayslipRevision(revision)
    ? revision
    : Number.NaN;
}

export function payrollPayslipRevisionLinks(
  parameters: URLSearchParams,
  input: {
    id: string;
    company: CompanyId;
    scope: PayrollPayslipScope;
    requested: number | null;
    viewed: number | null;
    current: number | null;
  },
) {
  const viewed = input.viewed,
    newer = viewed !== null && input.current !== null && input.current > viewed;
  const path = (revision: number | null): string | null => {
    if (!isUuid(input.id) || (revision !== null && !validPayrollPayslipRevision(revision)))
      return null;
    const next = new URLSearchParams(parameters);
    next.set("company", input.company);
    next.set("scope", input.scope);
    if (revision === null) next.delete("revision");
    else next.set("revision", String(revision));
    return `/payroll/payslips/${input.id.toLowerCase()}?${next}`;
  };
  return {
    previousTo: viewed !== null && viewed > 0 ? path(viewed - 1) : null,
    nextTo: viewed !== null && newer ? path(viewed + 1) : null,
    originalTo: viewed !== null && viewed > 1 ? path(0) : null,
    latestTo: input.requested !== null || newer ? path(null) : null,
    retained:
      input.requested !== null && validPayrollPayslipRevision(input.requested) && viewed !== null,
    newer,
  };
}

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
