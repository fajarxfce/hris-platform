import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { canReadOnboardingSchedules } from "../policies/onboarding-schedule-policy";
import type { OnboardingScheduleRepository } from "../repositories/onboarding-schedule-repository";
export class LoadOnboardingSchedules {
  constructor(private readonly schedules: OnboardingScheduleRepository) {}
  execute(access: CompanyAccess, employee: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadOnboardingSchedules(access.permissions))
      return Promise.resolve(failed("onboarding_access_required"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    return this.schedules.list(access.companyId, employee.toLowerCase() as EmployeeId, signal);
  }
}
