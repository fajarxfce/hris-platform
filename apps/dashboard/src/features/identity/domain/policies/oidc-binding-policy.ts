import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { OidcBindingChange } from "../entities/oidc-binding-change";
import { accountAccessSaveWasRejected } from "./account-access-policy";

export function validOidcSubject(value: string): boolean {
  return value.length >= 1 && value.length <= 255 && !/\p{Cc}/u.test(value);
}

export function oidcBindingFailure(input: OidcBindingChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.accountId)) fields.accountId = "invalid_identifier";
  if (!isUuid(input.id)) fields.id = "invalid_identifier";
  if (input.issuer.length < 1 || input.issuer.length > 500) fields.issuer = "invalid_value";
  if (!validOidcSubject(input.subject)) fields.subject = "invalid_value";
  if (typeof input.active !== "boolean") fields.active = "invalid_value";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion >= Number.MAX_SAFE_INTEGER)
  )
    fields.expectedVersion = "invalid_version";
  if (input.reason.trim().length < 1 || input.reason.length > 1000)
    fields.reason = "invalid_reason";
  return Object.keys(fields).length
    ? { code: "invalid_oidc_identity", fields, parameters: {} }
    : null;
}

export function oidcBindingSaveWasRejected(failure: Failure): boolean {
  return (
    accountAccessSaveWasRejected(failure) ||
    [
      "invalid_oidc_identity",
      "oidc_identity_not_found",
      "oidc_binding_immutable",
      "oidc_identity_limit",
      "account_disabled",
    ].includes(failure.code)
  );
}
