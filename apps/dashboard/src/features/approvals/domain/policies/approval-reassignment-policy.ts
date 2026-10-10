import type { AccountId } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalRequest } from "../entities/approval-request";
import { canManageApprovals } from "./approval-read-policy";

export const canReassignApproval = (access: CompanyAccess, request: ApprovalRequest): boolean =>
  access.companyId === request.companyId &&
  canManageApprovals(access.permissions) &&
  (request.status === "PENDING" || request.status === "BLOCKED");

/** Makers and beneficiaries remain excluded when the current stage is reassigned. */
export const excludedApprovalAccounts = (request: ApprovalRequest): readonly AccountId[] =>
  Object.freeze([
    ...new Set([
      request.authorId,
      ...(request.requesterId ? [request.requesterId] : []),
      ...request.excludedAccountIds,
    ]),
  ]);

export const approvalReassignmentWasRejected = (failure: Failure): boolean =>
  [
    "invalid_approval_reassignment",
    "approval_not_found",
    "approval_changed",
    "approver_unavailable",
    "stale_version",
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
