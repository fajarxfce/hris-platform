import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTaskAssignment } from "../entities/lifecycle-task-assignment";
import { normalizeLifecycleTaskAssignment } from "../policies/lifecycle-task-assignment-policy";
import { canManageLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class AssignLifecycleTask {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LifecycleTaskAssignment,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_lifecycle_assignment"));
    const change = normalizeLifecycleTaskAssignment(input);
    if (!change.ok) return Promise.resolve(change);
    return this.cases.assignTask(access.companyId, operation, change.value, signal);
  }
}
