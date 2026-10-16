import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { AccountAccessChange } from "../entities/account-access-change";
import type { PlatformAccess } from "../entities/platform-access";
export function canManagePlatformAccounts(access: PlatformAccess): boolean {
  return isUuid(access.accountId) && access.permissions.includes("identity.manage");
}
export function normalizeAccountAccessChange(input: AccountAccessChange): AccountAccessChange {
  return Object.freeze({
    ...input,
    accountId: input.accountId.toLowerCase() as AccountAccessChange["accountId"],
    platformPermissions: Object.freeze([...input.platformPermissions].sort()),
    reason: input.reason.trim(),
  });
}
export function accountAccessFailure(
  access: PlatformAccess,
  input: AccountAccessChange,
): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.accountId)) fields.accountId = "invalid_identifier";
  if (
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion >= Number.MAX_SAFE_INTEGER
  )
    fields.expectedVersion = "invalid_version";
  if (typeof input.active !== "boolean") fields.active = "invalid_value";
  if (
    input.platformPermissions.length > 100 ||
    new Set(input.platformPermissions).size !== input.platformPermissions.length ||
    input.platformPermissions.some((permission) => !/^[a-z][a-z0-9_.:-]{0,99}$/u.test(permission))
  )
    fields.platformPermissions = "invalid_permissions";
  if (input.reason.length < 1 || input.reason.length > 1000) fields.reason = "invalid_reason";
  if (Object.keys(fields).length) return { code: "invalid_account_access", fields, parameters: {} };
  if (
    input.accountId === access.accountId.toLowerCase() &&
    (!input.active || !input.platformPermissions.includes("identity.manage"))
  )
    return { code: "cannot_remove_own_administration", fields: {}, parameters: {} };
  return null;
}
export function accountAccessSaveWasRejected(failure: Failure): boolean {
  return [
    "invalid_account_access",
    "invalid_operation_id",
    "invalid_identifier",
    "invalid_request",
    "invalid_json",
    "request_body_too_large",
    "request_rate_limited",
    "data_conflict",
    "operation_payload_mismatch",
    "csrf_invalid",
    "access_denied",
    "platform_administrator_required",
    "platform_scope_required",
    "authentication_required",
    "unauthenticated",
    "session_revoked",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "account_not_found",
    "stale_version",
    "invitation_acceptance_required",
    "cannot_remove_own_administration",
  ].includes(failure.code);
}
