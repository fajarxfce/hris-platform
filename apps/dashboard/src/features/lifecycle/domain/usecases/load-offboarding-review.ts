import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseId } from "../entities/lifecycle-case";
import { canReadOffboardingReview } from "../policies/offboarding-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class LoadOffboardingReview {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadOffboardingReview(access.permissions))
      return Promise.resolve(failed("offboarding_access_required"));
    if (!isUuid(id)) return Promise.resolve(failed("lifecycle_case_not_found"));
    return this.cases.offboardingReview(
      access.companyId,
      id.toLowerCase() as LifecycleCaseId,
      signal,
    );
  }
}
