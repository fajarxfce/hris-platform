import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type LifecycleTaskStatus, lifecycleTaskStatuses } from "../entities/lifecycle-task";
import type { LifecycleTaskChange } from "../entities/lifecycle-task-change";
import type { LifecycleTaskContext } from "../entities/lifecycle-task-context";
import { canManageLifecycle, isLifecycleTaskKey } from "./lifecycle-template-policy";

/** Available transitions for an observed task; the server rechecks current authority. */
export function availableLifecycleTaskStatuses(
  access: CompanyAccess,
  account: AccountId,
  context: LifecycleTaskContext,
): readonly LifecycleTaskStatus[] {
  const manager = canManageLifecycle(access.permissions);
  if (
    context.companyId !== access.companyId ||
    context.caseStatus !== "OPEN" ||
    context.caseVersion === Number.MAX_SAFE_INTEGER ||
    (!manager &&
      (!access.permissions.includes("people.lifecycle.perform") ||
        context.task.assigneeId !== account))
  )
    return [];
  return Object.freeze(
    lifecycleTaskStatuses.filter(
      (status) =>
        status !== context.task.status &&
        (status !== "WAIVED" || (manager && !context.task.required)),
    ),
  );
}

export function normalizeLifecycleTaskChange(
  input: LifecycleTaskChange,
): Result<LifecycleTaskChange> {
  const change = Object.freeze({
    caseId: input.caseId.toLowerCase(),
    taskKey: input.taskKey.trim(),
    expectedVersion: input.expectedVersion,
    status: input.status,
    reason: input.reason.trim(),
  });
  if (
    !isUuid(change.caseId) ||
    !isLifecycleTaskKey(change.taskKey) ||
    !Number.isSafeInteger(change.expectedVersion) ||
    change.expectedVersion < 0 ||
    change.expectedVersion === Number.MAX_SAFE_INTEGER ||
    !lifecycleTaskStatuses.includes(change.status) ||
    change.reason.length === 0 ||
    change.reason.length > 1000
  )
    return failed("invalid_lifecycle_change");
  return success(change);
}

/** A rejected retry does not resolve a previously unacknowledged command. */
export function lifecycleTaskMutationWasRejected(failure: Failure): boolean {
  return [
    "invalid_lifecycle_change",
    "invalid_lifecycle_assignment",
    "lifecycle_assignee_unavailable",
    "lifecycle_task_not_assignable",
    "lifecycle_access_required",
    "lifecycle_case_not_found",
    "lifecycle_task_not_found",
    "lifecycle_task_not_assigned",
    "lifecycle_case_not_open",
    "lifecycle_task_cannot_be_waived",
    "lifecycle_task_unchanged",
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
