import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { ShiftChange } from "../entities/shift-change";
import { validateShiftDetails } from "./shift-policy";

export function normalizeShiftChange(input: ShiftChange): Result<ShiftChange> {
  const details = Object.freeze({
    ...input.details,
    code: input.details.code.trim().toUpperCase(),
    name: input.details.name.trim(),
    timezone: input.details.timezone.trim(),
    fence: input.details.fence ? Object.freeze({ ...input.details.fence }) : null,
  });
  const validated = validateShiftDetails(details);
  if (!validated.ok) return validated;
  if (
    !isUuid(input.id) ||
    !input.reason.trim() ||
    input.reason.length > 1000 ||
    (input.expectedVersion !== null &&
      (!Number.isSafeInteger(input.expectedVersion) ||
        input.expectedVersion < 0 ||
        input.expectedVersion === Number.MAX_SAFE_INTEGER))
  )
    return failed("invalid_shift_change");
  return success(
    Object.freeze({ ...input, id: input.id.toLowerCase(), details, reason: input.reason.trim() }),
  );
}

/** A first, explicit rejection is editable. An earlier unknown outcome remains pinned. */
export function shiftSaveWasRejected(failure: Failure): boolean {
  return [
    "invalid_shift",
    "invalid_shift_change",
    "invalid_shift_duration",
    "invalid_location_accuracy",
    "invalid_geofence",
    "stale_version",
    "data_conflict",
    "shift_not_found",
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
}
