import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCase } from "../entities/lifecycle-case";
import type { LifecycleCaseChange } from "../entities/lifecycle-case-change";
import { canManageLifecycle } from "./lifecycle-template-policy";

export function lifecycleCaseActions(access: CompanyAccess, details: LifecycleCase) {
  const cancel =
    canManageLifecycle(access.permissions) &&
    details.companyId === access.companyId &&
    details.status === "OPEN" &&
    details.version < Number.MAX_SAFE_INTEGER;
  return {
    cancel,
    completeOnboarding:
      cancel &&
      details.kind === "ONBOARDING" &&
      details.tasks.every((task) =>
        task.required ? task.status === "DONE" : task.status !== "PENDING",
      ),
  };
}
export function normalizeLifecycleCaseChange(
  input: LifecycleCaseChange,
): Result<LifecycleCaseChange> {
  const change = Object.freeze({
    caseId: input.caseId.toLowerCase(),
    expectedVersion: input.expectedVersion,
    reason: input.reason.trim(),
  });
  if (
    !isUuid(change.caseId) ||
    !Number.isSafeInteger(change.expectedVersion) ||
    change.expectedVersion < 0 ||
    change.expectedVersion === Number.MAX_SAFE_INTEGER ||
    change.reason.length === 0 ||
    change.reason.length > 1000
  )
    return failed("invalid_lifecycle_change");
  return success(change);
}
export const lifecycleCaseChangeWasRejected = (failure: Failure): boolean =>
  [
    "invalid_lifecycle_change",
    "lifecycle_case_not_found",
    "lifecycle_case_not_open",
    "required_lifecycle_tasks_pending",
    "lifecycle_tasks_unresolved",
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
