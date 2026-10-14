import type { Failure } from "../../../../core/domain/result";
import {
  payrollCalculationAccessLost,
  payrollCalculationCommandRejected,
} from "./payroll-calculation-policy";
export function payrollReviewReasonFailure(reason: string, required = true): Failure | null {
  return reason.length > 1000 || (required && !reason.trim())
    ? { code: "invalid_payroll_review", fields: { reason: "invalid" }, parameters: {} }
    : null;
}
export const payrollReviewAccessLost = (failure: Failure): boolean =>
  payrollCalculationAccessLost(failure) ||
  ["payroll_review_not_found", "not_assigned_approver", "self_approval_denied"].includes(
    failure.code,
  );
export const payrollReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "approval_changed",
    "approval_not_pending",
    "payroll_review_action_unavailable",
    "payroll_calculation_required",
    "payroll_calculation_has_failures",
    "payroll_review_active",
    "payroll_recalculation_required",
    "payroll_review_capacity",
    "payroll_review_not_pending",
    "payroll_review_not_active",
    "payroll_review_not_current",
    "payroll_finalization_active",
    "approval_policy_missing",
    "approval_unavailable",
    "ambiguous_approval_policy",
    "approval_policy_empty",
    "approval_group_too_large",
    "approval_delegation_capacity",
    "payroll_approval_assignment_invalid",
  ].includes(failure.code);
export const payrollReviewCommandRejected = (failure: Failure): boolean =>
  payrollCalculationCommandRejected(failure) ||
  payrollReviewAccessLost(failure) ||
  payrollReviewChanged(failure) ||
  ["invalid_payroll_review", "decision_reason_required"].includes(failure.code);
