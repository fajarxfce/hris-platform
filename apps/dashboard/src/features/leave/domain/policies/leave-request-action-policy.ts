import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  LeaveActionSnapshot,
  LeaveRequestChange,
  LeaveRequestIntent,
} from "../entities/leave-request-action";

export const hasLeaveActionPermission = (
  permissions: readonly string[],
  intent: LeaveRequestIntent,
): boolean =>
  (intent === "approve" || intent === "reject"
    ? ["leave.approve", "leave.team.approve"]
    : ["leave.manage", "leave.self.manage"]
  ).some((permission) => permissions.includes(permission));

/** Resource ownership, assignment and delegation come from the current server review. */
export const canPerformLeaveAction = (
  access: CompanyAccess,
  request: LeaveActionSnapshot,
  intent: LeaveRequestIntent,
): boolean =>
  access.companyId === request.companyId &&
  hasLeaveActionPermission(access.permissions, intent) &&
  (intent === "cancel"
    ? request.status === "APPROVED" && request.availableActions.includes("REQUEST_CANCELLATION")
    : ["PENDING", "CANCELLATION_PENDING"].includes(request.status) &&
      request.availableActions.includes(intent === "withdraw" ? "WITHDRAW" : "DECIDE"));

export function prepareLeaveAction(
  access: CompanyAccess,
  operation: OperationId,
  request: LeaveActionSnapshot,
  intent: LeaveRequestIntent,
  inputReason: string,
): Result<LeaveRequestChange> {
  if (
    access.companyId !== request.companyId ||
    !hasLeaveActionPermission(access.permissions, intent)
  )
    return failed("access_denied");
  if (!canPerformLeaveAction(access, request, intent)) return failed("leave_action_unavailable");
  const reason = inputReason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(request.id) ||
    !Number.isSafeInteger(request.version) ||
    request.version < 0 ||
    request.version >= Number.MAX_SAFE_INTEGER ||
    reason.length > 1000 ||
    (intent !== "approve" && reason.length === 0)
  )
    return failed(
      intent === "withdraw"
        ? "invalid_leave_withdrawal"
        : intent === "cancel"
          ? "invalid_leave_cancellation"
          : "invalid_leave_decision",
    );
  return success(Object.freeze({ id: request.id, version: request.version, reason }));
}

export const leaveActionReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "leave_action_unavailable",
    "leave_request_not_found",
    "leave_not_pending",
    "leave_not_approved",
    "approval_not_pending",
    "approval_changed",
    "approval_unavailable",
    "self_approval_denied",
    "not_assigned_approver",
    "payroll_period_frozen",
    "leave_year_closed",
  ].includes(failure.code);

/** Only definite first-attempt rejections release a command; uncertainty retains its receipt key. */
export const leaveActionWasRejected = (failure: Failure): boolean =>
  leaveActionReviewChanged(failure) ||
  [
    "invalid_leave_decision",
    "invalid_leave_withdrawal",
    "invalid_leave_cancellation",
    "decision_reason_required",
    "approval_policy_missing",
    "approval_policy_empty",
    "ambiguous_approval_policy",
    "approval_group_too_large",
    "approval_policy_capacity",
    "approval_delegation_capacity",
    "approval_exclusion_capacity",
    "data_conflict",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
