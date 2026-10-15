import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPayslipScope, PayrollPayslipSearch } from "../entities/payroll-payslip";
import { validPayrollMonth, validPayrollPeriodSearch } from "./payroll-period-policy";

export const canReadPayrollPayslips = (permissions: readonly string[]): boolean =>
  permissions.includes("payroll.read") || permissions.includes("payroll.self.read");
export const validPayrollPayslipScope = (scope: PayrollPayslipScope): boolean =>
  scope === "AUTHORIZED" || scope === "SELF";
export const validPayrollPayslipRevision = (revision: number): boolean =>
  Number.isSafeInteger(revision) && revision >= 0;
export function payrollPayslipSearchFailure(search: PayrollPayslipSearch): Failure | null {
  if (search.query.length > 120 || /\p{Cc}/u.test(search.query))
    return {
      code: "invalid_payroll_payslip_search",
      fields: { query: "invalid" },
      parameters: { maximumLength: "120" },
    };
  if (
    !validPayrollPayslipScope(search.scope) ||
    !validPayrollPeriodSearch({
      from: search.from,
      until: search.until,
      after: null,
      status: null,
    }) ||
    (search.employeeId !== null && !isUuid(search.employeeId)) ||
    (search.after !== null &&
      (search.after.length !== 44 ||
        search.after[7] !== ":" ||
        !validPayrollMonth(search.after.slice(0, 7)) ||
        !isUuid(search.after.slice(8)) ||
        search.after.slice(0, 7) < search.from ||
        search.after.slice(0, 7) > search.until))
  )
    return { code: "invalid_page", fields: {}, parameters: {} };
  return null;
}
export const payrollPayslipAccessLost = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_not_found",
    "company_required",
    "authentication_required",
    "unauthenticated",
    "session_revoked",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "payroll_payslip_not_found",
  ].includes(failure.code);
