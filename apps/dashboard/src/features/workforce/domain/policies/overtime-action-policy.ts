import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeActionSnapshot } from "../entities/overtime-action-snapshot";
import type { OvertimeRequestChange } from "../entities/overtime-request-change";
import { type OvertimeAction, overtimeActions } from "../entities/overtime-request-details";

export const hasOvertimeActionPermission = (
  permissions: readonly string[],
  action: OvertimeAction,
): boolean =>
  overtimeActions.includes(action) &&
  (action === "APPROVE" || action === "REJECT"
    ? ["overtime.approve"]
    : ["overtime.manage", "overtime.self.manage"]
  ).some((permission) => permissions.includes(permission));

/** Current resource ownership, assignment, period locks and maker exclusions remain server-owned. */
export const canPerformOvertimeAction = (
  access: CompanyAccess,
  review: OvertimeActionSnapshot,
  action: OvertimeAction,
): boolean =>
  access.companyId === review.companyId &&
  hasOvertimeActionPermission(access.permissions, action) &&
  review.actions.includes(action) &&
  (action === "SUBMIT_ACTUAL"
    ? review.status === "PLANNED"
    : action === "WITHDRAW"
      ? ["PLANNED", "PENDING"].includes(review.status)
      : review.status === "PENDING");

export function prepareOvertimeChange(
  access: CompanyAccess,
  operation: OperationId,
  review: OvertimeActionSnapshot,
  action: OvertimeAction,
  inputReason: string,
): Result<OvertimeRequestChange> {
  if (
    access.companyId !== review.companyId ||
    !hasOvertimeActionPermission(access.permissions, action)
  )
    return failed("access_denied");
  if (!canPerformOvertimeAction(access, review, action))
    return failed("overtime_action_unavailable");
  const reason = inputReason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(review.id) ||
    !Number.isInteger(review.version) ||
    review.version < 0 ||
    review.version > 998 ||
    reason.length > 1000 ||
    (action !== "APPROVE" && reason.length === 0)
  ) {
    return failed(
      action === "SUBMIT_ACTUAL"
        ? "invalid_overtime_actual"
        : action === "WITHDRAW"
          ? "invalid_overtime_withdrawal"
          : "invalid_overtime_decision",
    );
  }
  return success(Object.freeze({ id: review.id, version: review.version, reason }));
}

export const overtimeActionReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "overtime_action_unavailable",
    "overtime_not_planned",
    "overtime_not_pending",
    "overtime_request_not_found",
    "work_period_locked",
    "approval_not_pending",
    "approval_changed",
    "approval_unavailable",
    "self_approval_denied",
    "not_assigned_approver",
  ].includes(failure.code);

/** A denied read stays hidden even when the original command must remain available for retry. */
export const overtimeActionReadDenied = (failure: Failure): boolean =>
  [
    "access_denied",
    "overtime_request_not_found",
    "company_access_denied",
    "company_not_found",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
  ].includes(failure.code);

/** Only a definite first-attempt rejection may release a command's original receipt key. */
export const overtimeActionWasRejected = (failure: Failure): boolean =>
  overtimeActionReviewChanged(failure) ||
  overtimeActionReadDenied(failure) ||
  [
    "invalid_overtime_actual",
    "invalid_overtime_interval",
    "invalid_overtime_decision",
    "invalid_overtime_withdrawal",
    "overtime_outside_requested_window",
    "overtime_not_ended",
    "decision_reason_required",
    "approval_policy_missing",
    "approval_policy_empty",
    "ambiguous_approval_policy",
    "approval_group_too_large",
    "approval_policy_capacity",
    "approval_delegation_capacity",
    "approval_exclusion_capacity",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
