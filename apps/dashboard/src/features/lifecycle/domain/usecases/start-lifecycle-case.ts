import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseStart } from "../entities/lifecycle-case-start";
import { normalizeLifecycleCaseStart } from "../policies/lifecycle-case-start-policy";
import { canManageLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class StartLifecycleCase {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LifecycleCaseStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_lifecycle_case"));
    const command = normalizeLifecycleCaseStart(input);
    if (!command.ok) return Promise.resolve(command);
    return this.cases.start(access.companyId, operation, command.value, signal);
  }
}
