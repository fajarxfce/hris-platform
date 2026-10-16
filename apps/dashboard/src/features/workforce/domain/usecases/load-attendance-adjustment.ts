import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { canReadWorkPeriodSnapshot, isWorkPeriodMonth } from "../policies/work-period-policy";
import type { AttendanceAdjustmentRepository } from "../repositories/attendance-adjustment-repository";

export class LoadAttendanceAdjustment {
  constructor(private readonly adjustments: AttendanceAdjustmentRepository) {}
  execute(access: CompanyAccess, employee: string, month: string, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadWorkPeriodSnapshot(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    if (!isWorkPeriodMonth(month)) return Promise.resolve(failed("invalid_work_period_range"));
    if (!isUuid(id)) return Promise.resolve(failed("attendance_adjustment_not_found"));
    return this.adjustments.details(
      access.companyId,
      employee.toLowerCase() as EmployeeId,
      month,
      id.toLowerCase(),
      signal,
    );
  }
}
