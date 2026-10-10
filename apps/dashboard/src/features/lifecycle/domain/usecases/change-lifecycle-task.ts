import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTaskChange } from "../entities/lifecycle-task-change";
import { normalizeLifecycleTaskChange } from "../policies/lifecycle-task-change-policy";
import { canManageLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class ChangeLifecycleTask {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LifecycleTaskChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    const manager = canManageLifecycle(access.permissions);
    if (!manager && !access.permissions.includes("people.lifecycle.perform"))
      return Promise.resolve(failed("lifecycle_access_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_lifecycle_change"));
    const change = normalizeLifecycleTaskChange(input);
    if (!change.ok) return Promise.resolve(change);
    if (!manager && change.value.status === "WAIVED")
      return Promise.resolve(failed("lifecycle_task_cannot_be_waived"));
    return this.cases.changeTask(access.companyId, operation, change.value, signal);
  }
}
