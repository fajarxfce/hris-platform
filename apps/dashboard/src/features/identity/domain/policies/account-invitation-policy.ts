import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { AccountInvitation } from "../entities/account-invitation";
export function validAccountEmail(email: string): boolean {
  if (email.length < 3 || email.length > 254 || /[^\x21-\x7e]/u.test(email)) return false;
  const [local, domain, extra] = email.split("@");
  return (
    extra === undefined &&
    !!local &&
    !!domain &&
    local.length <= 64 &&
    !local.startsWith(".") &&
    !local.endsWith(".") &&
    !local.includes("..") &&
    /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+$/u.test(local) &&
    domain.split(".").length >= 2 &&
    domain
      .split(".")
      .every(
        (label) =>
          label.length >= 1 &&
          label.length <= 63 &&
          /^[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?$/u.test(label),
      )
  );
}
export function normalizeAccountInvitation(input: AccountInvitation): AccountInvitation {
  return Object.freeze({
    accountId: input.accountId.toLowerCase() as AccountInvitation["accountId"],
    email: input.email.trim().toLowerCase(),
    displayName: input.displayName.trim(),
    reason: input.reason.trim(),
    expectedVersion: input.expectedVersion,
  });
}
export function accountInvitationFailure(input: AccountInvitation): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.accountId)) fields.accountId = "invalid_identifier";
  if (!validAccountEmail(input.email)) fields.email = "invalid_email";
  if (
    !input.displayName.length ||
    input.displayName.length > 200 ||
    /[\p{Cc}]/u.test(input.displayName)
  )
    fields.displayName = "invalid_name";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_reason";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion >= Number.MAX_SAFE_INTEGER)
  )
    fields.expectedVersion = "invalid_version";
  return Object.keys(fields).length ? { code: "invalid_invitation", fields, parameters: {} } : null;
}
export function accountInvitationWasRejected(failure: Failure): boolean {
  return [
    "invalid_invitation",
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
    "account_cannot_be_invited",
    "mail_not_configured",
  ].includes(failure.code);
}
