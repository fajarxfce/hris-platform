import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OnboardingScheduleSelection } from "../entities/onboarding-schedule-selection";
import {
  canReadOnboardingSchedules,
  normalizeOnboardingSelection,
} from "../policies/onboarding-schedule-policy";
import type { OnboardingScheduleRepository } from "../repositories/onboarding-schedule-repository";
export class LoadOnboardingScheduleReview {
  constructor(private readonly schedules: OnboardingScheduleRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    selection: OnboardingScheduleSelection,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadOnboardingSchedules(access.permissions))
      return Promise.resolve(failed("onboarding_access_required"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    const normalized = normalizeOnboardingSelection(selection);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.schedules.review(
      access.companyId,
      employee.toLowerCase() as EmployeeId,
      normalized.value,
      signal,
    );
  }
}
