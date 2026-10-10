import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OffboardingCompletion } from "../entities/offboarding-completion";
import {
  canCompleteOffboarding,
  normalizeOffboardingCompletion,
} from "../policies/offboarding-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class CompleteOffboarding {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: OffboardingCompletion,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canCompleteOffboarding(access.permissions))
      return Promise.resolve(failed("offboarding_access_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_offboarding"));
    const change = normalizeOffboardingCompletion(input);
    if (!change.ok) return Promise.resolve(change);
    return this.cases.completeOffboarding(access.companyId, operation, change.value, signal);
  }
}
