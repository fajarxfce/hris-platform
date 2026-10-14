import type { Failure } from "../../../../core/domain/result";
import {
  type PayrollPublicationSearch,
  payrollPublicationStatuses,
} from "../entities/payroll-publication-search";
import {
  payrollCalculationAccessLost,
  payrollCalculationCommandRejected,
} from "./payroll-calculation-policy";
import { canReadPayrollPeriods, validPayrollPeriodSearch } from "./payroll-period-policy";
export const canReadPayrollFinalizations = (permissions: readonly string[]): boolean =>
  canReadPayrollPeriods(permissions) || permissions.includes("payroll.finalize");
export const validPayrollPublicationSearch = (search: PayrollPublicationSearch): boolean =>
  (search.status === null || payrollPublicationStatuses.includes(search.status)) &&
  validPayrollPeriodSearch({ ...search, status: null });
export const payrollFinalizationReasonFailure = (reason: string): Failure | null =>
  !reason.trim() || reason.length > 1000
    ? { code: "invalid_payroll_finalization", fields: { reason: "invalid" }, parameters: {} }
    : null;
export const payrollFinalizationAccessLost = (failure: Failure): boolean =>
  payrollCalculationAccessLost(failure) ||
  ["payroll_finalization_not_found", "payroll_finalization_obsolete"].includes(failure.code);
export const payrollFinalizationChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "approval_changed",
    "stale_employment_version",
    "payroll_finalization_action_unavailable",
    "payroll_already_finalized",
    "payroll_successful_calculation_required",
    "payroll_approved_review_required",
    "payroll_finalization_active",
    "payroll_finalization_capacity",
    "payroll_person_duplicated",
    "payroll_holiday_already_assessed",
    "payroll_tax_history_changed",
  ].includes(failure.code);
export const payrollFinalizationCommandRejected = (failure: Failure): boolean =>
  payrollCalculationCommandRejected(failure) ||
  payrollFinalizationAccessLost(failure) ||
  payrollFinalizationChanged(failure) ||
  failure.code === "invalid_payroll_finalization";
