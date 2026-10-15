import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OnboardingScheduleChange } from "../entities/onboarding-schedule-command";
import {
  canManageOnboardingSchedules,
  normalizeOnboardingScheduleChange,
} from "../policies/onboarding-schedule-policy";
import type { OnboardingScheduleRepository } from "../repositories/onboarding-schedule-repository";
export class CancelOnboardingSchedule {
  constructor(private readonly schedules: OnboardingScheduleRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: OnboardingScheduleChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageOnboardingSchedules(access.permissions))
      return Promise.resolve(failed("onboarding_access_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_onboarding_schedule"));
    const normalized = normalizeOnboardingScheduleChange(input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.schedules.cancel(access.companyId, operation, normalized.value, signal);
  }
}
