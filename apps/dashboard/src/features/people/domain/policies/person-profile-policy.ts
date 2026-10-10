import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  PersonProfile,
  PersonProfileChange,
  PersonProfileFields,
} from "../entities/person-profile";

export const canReadPersonProfile = (permissions: readonly string[]): boolean =>
  permissions.includes("people.profile.read") || permissions.includes("people.self.read");
export const canReadProfileHistory = (permissions: readonly string[]): boolean =>
  permissions.includes("people.profile.read");
export const canManagePersonProfile = (access: CompanyAccess, profile: PersonProfile): boolean =>
  canReadPersonProfile(access.permissions) &&
  access.permissions.includes("people.profile.manage") &&
  access.companyId === profile.ownerCompanyId;
export const isProfileRevisionCursor = (value: string): boolean =>
  /^(0|[1-9]\d{0,15})$/u.test(value) && Number.isSafeInteger(Number(value));

export function normalizePersonProfileChange(
  input: PersonProfileChange,
): Result<PersonProfileChange> {
  const reason = input.reason.trim();
  if (
    !isUuid(input.personId) ||
    !isUuid(input.ownerCompanyId) ||
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion === Number.MAX_SAFE_INTEGER ||
    !reason ||
    reason.length > 1000
  )
    return failed("invalid_profile_change");
  const fields = normalizePersonFields(input);
  return fields.ok ? success(Object.freeze({ ...input, ...fields.value, reason })) : fields;
}

export function normalizePersonFields(input: PersonProfileFields): Result<PersonProfileFields> {
  const change = Object.freeze({
    legalName: input.legalName.trim(),
    nationality: input.nationality.trim().toUpperCase(),
    email: input.email?.trim().toLowerCase() || null,
    birthDate: input.birthDate || null,
  });
  const fields: Record<string, string> = {};
  if (!change.legalName || change.legalName.length > 200) fields.legalName = "invalid_name";
  if (!/^[A-Z]{2}$/u.test(change.nationality)) fields.nationality = "invalid_country";
  if (change.birthDate !== null && !isCalendarDate(change.birthDate))
    fields.birthDate = "invalid_birth_date";
  if (change.email !== null && (change.email.length > 254 || !change.email.includes("@")))
    fields.email = "invalid_email";
  if (Object.keys(fields).length > 0)
    return { ok: false, failure: { code: "invalid_person", fields, parameters: {} } };
  // Recognized ISO countries and future birth dates are validated against server policy.
  return success(change);
}

/** A known rejection cannot resolve a previous attempt whose result was lost. */
export function profileSaveWasRejected(failure: Failure): boolean {
  return [
    "invalid_profile_change",
    "invalid_person",
    "person_profile_not_found",
    "profile_owner_required",
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
}
