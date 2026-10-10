import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTaskAssignment } from "../entities/lifecycle-task-assignment";
import type { LifecycleTaskContext } from "../entities/lifecycle-task-context";
import { canManageLifecycle, isLifecycleTaskKey } from "./lifecycle-template-policy";

export function canAssignLifecycleTask(
  access: CompanyAccess,
  context: LifecycleTaskContext,
): boolean {
  return (
    canManageLifecycle(access.permissions) &&
    context.companyId === access.companyId &&
    context.caseStatus === "OPEN" &&
    context.task.status === "PENDING" &&
    context.caseVersion < Number.MAX_SAFE_INTEGER
  );
}
export function normalizeLifecycleTaskAssignment(
  input: LifecycleTaskAssignment,
): Result<LifecycleTaskAssignment> {
  const change = Object.freeze({
    caseId: input.caseId.toLowerCase(),
    taskKey: input.taskKey.trim(),
    expectedVersion: input.expectedVersion,
    assigneeId: input.assigneeId?.toLowerCase() ?? null,
    reason: input.reason.trim(),
  });
  if (
    !isUuid(change.caseId) ||
    !isLifecycleTaskKey(change.taskKey) ||
    !Number.isSafeInteger(change.expectedVersion) ||
    change.expectedVersion < 0 ||
    change.expectedVersion === Number.MAX_SAFE_INTEGER ||
    (change.assigneeId !== null && !isUuid(change.assigneeId)) ||
    change.reason.length === 0 ||
    change.reason.length > 1000
  )
    return failed("invalid_lifecycle_assignment");
  return success(change);
}
