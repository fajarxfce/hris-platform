import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OffboardingScheduleChange } from "../entities/offboarding-schedule-command";
import { canCompleteOffboarding } from "../policies/offboarding-policy";
import { normalizeOffboardingScheduleChange } from "../policies/offboarding-schedule-policy";
import type { OffboardingScheduleRepository } from "../repositories/offboarding-schedule-repository";
export class CancelOffboardingSchedule {
  constructor(private readonly schedules: OffboardingScheduleRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: OffboardingScheduleChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canCompleteOffboarding(access.permissions))
      return Promise.resolve(failed("offboarding_access_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_offboarding_schedule"));
    const normalized = normalizeOffboardingScheduleChange(input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.schedules.cancel(access.companyId, operation, normalized.value, signal);
  }
}
