import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { type OrganizationUnitKind, organizationUnitKinds } from "../entities/organization-unit";
import type { OrganizationUnitChange } from "../entities/organization-unit-change";
import { isOrganizationUnitCode, isOrganizationUnitId } from "./organization-unit-policy";

export const canManageOrganization = (permissions: readonly string[]): boolean =>
  permissions.includes("company.read") && permissions.includes("company.manage");

export const parentUnitKinds: Readonly<
  Record<OrganizationUnitKind, readonly OrganizationUnitKind[]>
> = {
  BRANCH: ["BRANCH"],
  DEPARTMENT: ["BRANCH", "DEPARTMENT"],
  POSITION: ["DEPARTMENT"],
  COST_CENTER: ["COST_CENTER"],
};

export function normalizeOrganizationChange(
  input: OrganizationUnitChange,
): Result<OrganizationUnitChange> {
  const change = Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    code: input.code.trim().toUpperCase(),
    name: input.name.trim(),
    parentId: input.parentId?.toLowerCase() ?? null,
    timezone: input.timezone?.trim() || null,
  });
  if (
    !isOrganizationUnitId(change.id) ||
    !isOrganizationUnitCode(change.code) ||
    change.name.length === 0 ||
    change.name.length > 200 ||
    !organizationUnitKinds.includes(change.kind) ||
    (change.parentId !== null && !isOrganizationUnitId(change.parentId)) ||
    (change.expectedVersion !== null &&
      (!Number.isSafeInteger(change.expectedVersion) ||
        change.expectedVersion < 0 ||
        change.expectedVersion === Number.MAX_SAFE_INTEGER))
  )
    return failed("invalid_organization_unit");
  if (change.parentId === change.id) return failed("organization_cycle_or_depth");
  if (
    change.kind === "BRANCH"
      ? change.timezone === null || change.timezone.length > 128
      : change.timezone !== null
  )
    return failed("invalid_unit_timezone");
  // The server validates IANA zones, live ancestry, depth and parent availability.
  return success(change);
}

/** Only explicit pre-commit rejections allow editing a first attempt. Unknown outcomes stay pinned. */
export function organizationSaveWasRejected(failure: Failure): boolean {
  return [
    "invalid_organization_unit",
    "invalid_unit_timezone",
    "organization_cycle_or_depth",
    "parent_unavailable",
    "invalid_parent_kind",
    "unit_kind_immutable",
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
