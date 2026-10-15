import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OnboardingScheduleId } from "../entities/onboarding-schedule";
import { canReadOnboardingSchedules } from "../policies/onboarding-schedule-policy";
import type { OnboardingScheduleRepository } from "../repositories/onboarding-schedule-repository";
export class LoadOnboardingSchedule {
  constructor(private readonly schedules: OnboardingScheduleRepository) {}
  execute(access: CompanyAccess, employee: string, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadOnboardingSchedules(access.permissions))
      return Promise.resolve(failed("onboarding_access_required"));
    if (!isUuid(employee) || !isUuid(id))
      return Promise.resolve(failed("onboarding_schedule_not_found"));
    return this.schedules.get(
      access.companyId,
      employee.toLowerCase() as EmployeeId,
      id.toLowerCase() as OnboardingScheduleId,
      signal,
    );
  }
}
