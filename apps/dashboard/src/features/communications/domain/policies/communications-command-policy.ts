import type { Failure } from "../../../../core/domain/result";

/** A known first rejection permits correction; it cannot resolve an older ambiguous request. */
export const communicationsCommandWasRejected = (failure: Failure): boolean =>
  [
    "invalid_version",
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
    "request_body_too_large",
  ].includes(failure.code);
