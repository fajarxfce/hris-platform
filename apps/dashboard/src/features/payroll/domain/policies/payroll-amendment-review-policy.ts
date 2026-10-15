import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentReviewWithdrawal } from "../entities/payroll-amendment-review-command";
import type { PayrollAmendmentReviewAction } from "../entities/payroll-amendment-review-details";
import {
  amendmentAccessLost,
  amendmentCommandRejected,
  amendmentContextChanged,
} from "./payroll-amendment-authoring-policy";
import { validatePayrollAmendmentCommand } from "./payroll-amendment-command-policy";

export const canActOnAmendmentReview = (
  permissions: readonly string[],
  action: PayrollAmendmentReviewAction,
): boolean =>
  permissions.includes(action === "WITHDRAW" ? "payroll.amend" : "payroll.amend.review");
export function validateAmendmentReviewTransition(
  input: PayrollAmendmentReviewWithdrawal,
): Failure | null {
  const common = validatePayrollAmendmentCommand(input);
  if (common) return common;
  if (!isUuid(input.amendmentId))
    return { code: "invalid_payroll_amendment", fields: {}, parameters: {} };
  if (
    !Number.isInteger(input.expectedReviewVersion) ||
    input.expectedReviewVersion < 0 ||
    input.expectedReviewVersion > 9 ||
    !Number.isSafeInteger(input.expectedApprovalVersion) ||
    input.expectedApprovalVersion < 0
  )
    return { code: "invalid_version", fields: {}, parameters: {} };
  return null;
}
export const amendmentReviewAccessLost = (failure: Failure): boolean =>
  amendmentAccessLost(failure) ||
  ["payroll_amendment_review_not_found", "not_assigned_approver", "self_approval_denied"].includes(
    failure.code,
  );
export const amendmentReviewChanged = (failure: Failure): boolean =>
  amendmentContextChanged(failure) ||
  [
    "payroll_amendment_calculation_required",
    "payroll_amendment_calculation_not_found",
    "payroll_amendment_review_active",
    "payroll_amendment_recalculation_required",
    "payroll_amendment_review_limit",
    "payroll_amendment_review_not_pending",
    "payroll_amendment_review_not_active",
    "payroll_amendment_review_not_current",
    "payroll_amendment_review_action_unavailable",
    "approval_changed",
    "approval_not_pending",
    "approval_policy_missing",
    "approval_unavailable",
    "ambiguous_approval_policy",
    "approval_policy_empty",
    "approval_group_too_large",
    "approval_delegation_capacity",
    "payroll_approval_assignment_invalid",
  ].includes(failure.code);
export const amendmentReviewCommandRejected = (failure: Failure): boolean =>
  amendmentCommandRejected(failure) ||
  amendmentReviewAccessLost(failure) ||
  amendmentReviewChanged(failure) ||
  failure.code === "decision_reason_required";
