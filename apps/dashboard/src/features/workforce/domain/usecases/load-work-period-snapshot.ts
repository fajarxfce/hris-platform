import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { canReadWorkPeriodSnapshot, isWorkPeriodMonth } from "../policies/work-period-policy";
import type { WorkPeriodRepository } from "../repositories/work-period-repository";

export class LoadWorkPeriodSnapshot {
  constructor(private readonly periods: WorkPeriodRepository) {}
  execute(access: CompanyAccess, month: string, employeeId: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadWorkPeriodSnapshot(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isWorkPeriodMonth(month)) return Promise.resolve(failed("invalid_work_period_range"));
    if (!isUuid(employeeId)) return Promise.resolve(failed("employee_not_found"));
    return this.periods.snapshot(
      access.companyId,
      month,
      employeeId.toLowerCase() as EmployeeId,
      signal,
    );
  }
}
