import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { HolidayChange } from "../entities/holiday-change";

export function normalizeHolidayChange(input: HolidayChange): Result<HolidayChange> {
  if (
    !isUuid(input.id) ||
    !isCalendarDate(input.workDate) ||
    !input.name.trim() ||
    input.name.length > 200 ||
    !input.reason.trim() ||
    input.reason.length > 1000 ||
    (input.expectedVersion !== null &&
      (!Number.isSafeInteger(input.expectedVersion) ||
        input.expectedVersion < 0 ||
        input.expectedVersion === Number.MAX_SAFE_INTEGER))
  )
    return failed("invalid_holiday");
  return success(
    Object.freeze({
      ...input,
      id: input.id.toLowerCase(),
      name: input.name.trim(),
      reason: input.reason.trim(),
    }),
  );
}

/** Only a first, explicit rejection permits editing; a prior unknown outcome stays pinned. */
export function holidaySaveWasRejected(failure: Failure): boolean {
  return [
    "invalid_holiday",
    "stale_version",
    "data_conflict",
    "holiday_not_found",
    "work_period_locked",
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
