import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { RoleTemplateChange } from "../entities/role-template-change";

export const canManageRoleTemplates = (permissions: readonly string[]): boolean =>
  permissions.includes("identity.manage");

export function normalizeRoleTemplateChange(input: RoleTemplateChange): RoleTemplateChange {
  return Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    code: input.code.trim().toUpperCase(),
    name: input.name.trim(),
    reason: input.reason.trim(),
    permissions: Object.freeze([...input.permissions].sort()),
  });
}
export function validateRoleTemplateChange(input: RoleTemplateChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.id)) fields.id = "invalid_value";
  if (!/^[A-Z0-9][A-Z0-9_-]{1,31}$/u.test(input.code)) fields.code = "invalid_code";
  if (!input.name.length || input.name.length > 200) fields.name = "invalid_name";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_reason";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion >= Number.MAX_SAFE_INTEGER)
  )
    fields.expectedVersion = "invalid_value";
  if (
    input.permissions.length > 200 ||
    new Set(input.permissions).size !== input.permissions.length ||
    input.permissions.some((value) => !/^[a-z][a-z0-9_.:-]{0,99}$/u.test(value))
  )
    fields.permissions = "invalid_permissions";
  return Object.keys(fields).length
    ? { code: "invalid_role_template", fields, parameters: {} }
    : null;
}
/** A later rejection cannot resolve an earlier command whose response was lost. */
export const roleTemplateSaveWasRejected = (failure: Failure): boolean =>
  [
    "invalid_role_template",
    "role_code_immutable",
    "role_template_not_found",
    "role_template_limit",
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
