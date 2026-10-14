import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import { payrollPeriodStatuses } from "../entities/payroll-period";
import type {
  PayrollPeriodCreation,
  PayrollPeriodFields,
} from "../entities/payroll-period-creation";
import type { PayrollPeriodSearch } from "../entities/payroll-period-search";
export const canReadPayrollPeriods = (permissions: readonly string[]): boolean =>
  permissions.some((value) =>
    ["payroll.read", "payroll.calculate", "payroll.review"].includes(value),
  );
export const canPreparePayrollPeriod = (permissions: readonly string[]): boolean =>
  permissions.includes("payroll.calculate");
export const validPayrollMonth = (value: string): boolean =>
  /^(?:202[4-9]|20[3-9]\d|2100)-(?:0[1-9]|1[0-2])$/u.test(value);
export function validPayrollPeriodSearch(search: PayrollPeriodSearch): boolean {
  if (
    !validPayrollMonth(search.from) ||
    !validPayrollMonth(search.until) ||
    search.until < search.from ||
    (search.after !== null && !isUuid(search.after)) ||
    (search.status !== null && !payrollPeriodStatuses.includes(search.status))
  )
    return false;
  const first = Number(search.from.slice(0, 4)) * 12 + Number(search.from.slice(5)),
    last = Number(search.until.slice(0, 4)) * 12 + Number(search.until.slice(5));
  return last - first <= 35;
}
export function validatePayrollPeriodFields(fields: PayrollPeriodFields): Failure | null {
  const errors: Record<string, string> = {};
  if (!validPayrollMonth(fields.earningsMonth)) errors.earningsMonth = "out_of_range";
  const date = fields.plannedPaymentDate;
  if (!isCalendarDate(date) || !validPayrollMonth(date.slice(0, 7)))
    errors.plannedPaymentDate = "out_of_range";
  else if (!errors.earningsMonth) {
    const end = new Date(`${fields.earningsMonth}-01T00:00:00.000Z`);
    end.setUTCMonth(end.getUTCMonth() + 1);
    end.setUTCDate(62);
    if (date < `${fields.earningsMonth}-01` || date > end.toISOString().slice(0, 10))
      errors.plannedPaymentDate = "out_of_range";
  }
  if (fields.reason.trim().length === 0 || fields.reason.length > 1000) errors.reason = "invalid";
  return Object.keys(errors).length
    ? { code: "invalid_payroll_period", fields: errors, parameters: {} }
    : null;
}
export function validatePayrollPeriodCreation(input: PayrollPeriodCreation): Failure | null {
  const failure = validatePayrollPeriodFields(input);
  const fields: Record<string, string> = { ...failure?.fields };
  if (!isUuid(input.id)) fields.id = "invalid";
  if (
    input.employeeIds.length < 1 ||
    input.employeeIds.length > 5000 ||
    input.employeeIds.some((id) => !isUuid(id)) ||
    new Set(input.employeeIds.map((id) => id.toLowerCase())).size !== input.employeeIds.length
  )
    fields.employeeIds = "out_of_range";
  return Object.keys(fields).length
    ? { code: "invalid_payroll_period", fields, parameters: {} }
    : null;
}
export const normalizePayrollPeriodCreation = (
  input: PayrollPeriodCreation,
): PayrollPeriodCreation =>
  Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    reason: input.reason.trim(),
    employeeIds: Object.freeze(input.employeeIds.map((id) => id.toLowerCase()).sort()),
  });
export const payrollPeriodAccessLost = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_not_found",
    "authentication_required",
    "unauthenticated",
    "session_revoked",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "payroll_period_not_found",
  ].includes(failure.code);
export const payrollPeriodCommandRejected = (failure: Failure): boolean =>
  payrollPeriodAccessLost(failure) ||
  [
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "invalid_payroll_period",
    "invalid_version",
    "payroll_period_exists",
    "payroll_period_active",
    "payroll_period_attempt_limit",
    "payroll_period_employee_unavailable",
    "payroll_period_not_draft",
    "payroll_period_revision_limit",
    "stale_version",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
