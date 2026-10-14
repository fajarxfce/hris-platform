import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { Failure } from "../../../../core/domain/result";
import type {
  PayrollCalculationFields,
  PayrollCalculationRecovery,
} from "../entities/payroll-calculation-command";
import {
  payrollPeriodAccessLost,
  payrollPeriodCommandRejected,
  validPayrollMonth,
} from "./payroll-period-policy";
export function validatePayrollCalculationFields(
  value: PayrollCalculationFields,
  month?: string,
): Failure | null {
  const fields: Record<string, string> = {};
  if (
    !isCalendarDate(value.incomeDueDate) ||
    !validPayrollMonth(value.incomeDueDate.slice(0, 7)) ||
    (month !== undefined && value.incomeDueDate.slice(0, 7) !== month)
  )
    fields.incomeDueDate = "out_of_range";
  if (
    !value.reviewReference.trim() ||
    value.reviewReference.length > 200 ||
    /[\p{Cc}]/u.test(value.reviewReference)
  )
    fields.reviewReference = "invalid";
  if (!value.reason.trim() || value.reason.length > 1000) fields.reason = "invalid";
  return Object.keys(fields).length
    ? { code: "invalid_payroll_run", fields, parameters: {} }
    : null;
}
export const validPayrollCalculationVersion = (
  value: number,
  max = Number.MAX_SAFE_INTEGER,
): boolean => Number.isSafeInteger(value) && value >= 0 && value <= max;
export const validPayrollCalculationRecovery = (value: PayrollCalculationRecovery): boolean =>
  validPayrollCalculationVersion(value.expectedVersion, 24) &&
  validPayrollCalculationVersion(value.expectedPeriodVersion, 254) &&
  value.reason.trim().length > 0 &&
  value.reason.length <= 1000;
export const payrollCalculationAccessLost = (failure: Failure): boolean =>
  payrollPeriodAccessLost(failure) ||
  [
    "mfa_required",
    "mfa_setup_required",
    "payroll_run_not_found",
    "payroll_run_result_not_found",
  ].includes(failure.code);
export const payrollCalculationReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "stale_policy_version",
    "stale_workforce_version",
    "stale_employment_version",
    "payroll_calculation_action_unavailable",
    "payroll_period_not_draft",
    "payroll_period_frozen",
    "payroll_period_revision_limit",
    "payroll_run_limit",
    "payroll_run_attempt_limit",
    "payroll_run_job_obsolete",
    "payroll_job_not_stopped",
    "payroll_run_not_resumable",
    "payroll_run_already_abandoned",
    "payroll_review_withdrawal_required",
    "payroll_workforce_not_closed",
    "payroll_timezone_review_required",
    "payroll_policy_not_found",
    "payroll_policy_not_effective",
    "payroll_leave_decision_pending",
  ].includes(failure.code);
export const payrollCalculationCommandRejected = (failure: Failure): boolean =>
  payrollPeriodCommandRejected(failure) ||
  payrollCalculationAccessLost(failure) ||
  payrollCalculationReviewChanged(failure) ||
  [
    "invalid_payroll_run",
    "invalid_payroll_run_action",
    "payroll_tax_month_review_required",
    "payroll_run_author_required",
    "job_queue_full",
  ].includes(failure.code);
