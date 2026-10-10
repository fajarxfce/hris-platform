import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalDelegation } from "../entities/approval-delegation";
import type { ApprovalDelegationChange } from "../entities/approval-delegation-change";
import { canManageApprovals, canReadApprovalInbox } from "./approval-read-policy";
import { isApprovalKind } from "./approval-template-policy";

export const canReadApprovalDelegation = (
  access: CompanyAccess,
  account: AccountId,
  delegation: ApprovalDelegation,
): boolean =>
  access.companyId === delegation.companyId &&
  canReadApprovalInbox(access.permissions) &&
  (canManageApprovals(access.permissions) ||
    delegation.fromAccount === account ||
    delegation.toAccount === account);

export const canEditApprovalDelegation = (
  access: CompanyAccess,
  account: AccountId,
  delegation: ApprovalDelegation,
): boolean =>
  access.companyId === delegation.companyId &&
  canReadApprovalInbox(access.permissions) &&
  (canManageApprovals(access.permissions) || delegation.fromAccount === account);

/** Durations use elapsed time, including when the company's clocks cross a DST change. */
export function validApprovalDelegationPeriod(from: string, until: string): boolean {
  if (from.length > 32 || until.length > 32) return false;
  const start = utcInstantMicroseconds(from);
  const end = utcInstantMicroseconds(until);
  return start !== null && end !== null && end > start && end - start <= 90n * 86_400_000_000n;
}
export function validApprovalDelegationChange(change: ApprovalDelegationChange): boolean {
  return (
    isUuid(change.id) &&
    isApprovalKind(change.kind) &&
    isUuid(change.fromAccount) &&
    isUuid(change.toAccount) &&
    change.fromAccount !== change.toAccount &&
    validApprovalDelegationPeriod(change.validFrom, change.validUntil) &&
    (change.expectedVersion === null ||
      (Number.isSafeInteger(change.expectedVersion) &&
        change.expectedVersion >= 0 &&
        change.expectedVersion < Number.MAX_SAFE_INTEGER)) &&
    change.reason.length >= 1 &&
    change.reason.length <= 1000
  );
}
export const normalizeApprovalDelegation = (
  input: ApprovalDelegationChange,
): ApprovalDelegationChange =>
  Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    fromAccount: input.fromAccount.toLowerCase() as AccountId,
    toAccount: input.toAccount.toLowerCase() as AccountId,
    reason: input.reason.trim(),
  });
/** An explicit rejection only resolves the first attempt, never an earlier lost response. */
export const approvalDelegationSaveWasRejected = (failure: Failure): boolean =>
  [
    "invalid_delegation",
    "delegator_immutable",
    "approval_delegation_not_found",
    "approval_delegation_limit",
    "approval_delegation_capacity",
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
