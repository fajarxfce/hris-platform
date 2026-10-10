import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseChange } from "../entities/lifecycle-case-change";
import { normalizeLifecycleCaseChange } from "../policies/lifecycle-case-change-policy";
import { canManageLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class CancelLifecycleCase {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LifecycleCaseChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_lifecycle_change"));
    const change = normalizeLifecycleCaseChange(input);
    if (!change.ok) return Promise.resolve(change);
    return this.cases.cancel(access.companyId, operation, change.value, signal);
  }
}
