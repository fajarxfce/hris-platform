import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseId } from "../entities/lifecycle-case";
import { canReadOffboardingReview } from "../policies/offboarding-policy";
import type { OffboardingScheduleRepository } from "../repositories/offboarding-schedule-repository";
export class LoadOffboardingSchedules {
  constructor(private readonly schedules: OffboardingScheduleRepository) {}
  execute(access: CompanyAccess, caseId: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadOffboardingReview(access.permissions))
      return Promise.resolve(failed("offboarding_access_required"));
    if (!isUuid(caseId)) return Promise.resolve(failed("offboarding_schedule_not_found"));
    return this.schedules.list(access.companyId, caseId.toLowerCase() as LifecycleCaseId, signal);
  }
}
