import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { CompanyMembershipChange } from "../entities/company-membership-change";

export function normalizeCompanyMembershipChange(
  input: CompanyMembershipChange,
): CompanyMembershipChange {
  return Object.freeze({
    ...input,
    accountId: input.accountId.toLowerCase(),
    reason: input.reason.trim(),
    permissions: Object.freeze([...input.permissions].sort()),
    roleTemplates: Object.freeze(
      input.roleTemplates
        .map((role) =>
          Object.freeze({
            ...role,
            id: role.id.toLowerCase(),
          }),
        )
        .sort((first, second) => first.id.localeCompare(second.id)),
    ),
  });
}

export function validateCompanyMembershipChange(input: CompanyMembershipChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.accountId)) fields.accountId = "invalid_value";
  if (
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion >= Number.MAX_SAFE_INTEGER
  )
    fields.expectedVersion = "invalid_value";
  if (typeof input.active !== "boolean") fields.active = "invalid_value";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_reason";
  if (
    input.permissions.length > 200 ||
    new Set(input.permissions).size !== input.permissions.length ||
    input.permissions.some((value) => !/^[a-z][a-z0-9_.:-]{0,99}$/u.test(value))
  )
    fields.permissions = "invalid_permissions";
  if (
    input.roleTemplates.length > 8 ||
    new Set(input.roleTemplates.map((role) => role.id)).size !== input.roleTemplates.length ||
    input.roleTemplates.some(
      (role) =>
        !isUuid(role.id) ||
        !Number.isSafeInteger(role.version) ||
        role.version < 0 ||
        typeof role.retainApplied !== "boolean",
    )
  )
    fields.roleTemplates = "invalid_roles";
  return Object.keys(fields).length ? { code: "invalid_membership", fields, parameters: {} } : null;
}

/** A later rejection does not settle an earlier request whose response was lost. */
export const companyMembershipSaveWasRejected = (failure: Failure): boolean =>
  [
    "invalid_membership",
    "account_unavailable",
    "company_member_not_found",
    "role_template_unavailable",
    "role_template_review_required",
    "stale_role_template",
    "cannot_remove_own_administration",
    "last_company_administrator",
    "cannot_self_grant_sensitive_access",
    "platform_administrator_required",
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
