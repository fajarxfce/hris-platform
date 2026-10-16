import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { isAttendanceAdjustmentDate } from "../policies/attendance-adjustment-policy";
import { canReadWorkPeriodSnapshot } from "../policies/work-period-policy";
import type { AttendanceAdjustmentRepository } from "../repositories/attendance-adjustment-repository";

export class LoadAttendanceAdjustmentReview {
  constructor(private readonly adjustments: AttendanceAdjustmentRepository) {}
  execute(access: CompanyAccess, employee: string, date: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadWorkPeriodSnapshot(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    if (!isAttendanceAdjustmentDate(date))
      return Promise.resolve(failed("invalid_attendance_adjustment_date"));
    return this.adjustments.review(
      access.companyId,
      employee.toLowerCase() as EmployeeId,
      date,
      signal,
    );
  }
}
