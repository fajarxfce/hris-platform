import type { Failure } from "../../../../core/domain/result";
export function validCredentialToken(token: string): boolean {
  return /^[A-Za-z0-9_-]{43}$/u.test(token);
}
export function credentialConfirmationWasRejected(failure: Failure): boolean {
  return [
    "password_length",
    "credential_link_unavailable",
    "csrf_invalid",
    "invalid_request",
    "invalid_json",
    "request_body_too_large",
    "request_rate_limited",
    "access_denied",
  ].includes(failure.code);
}
