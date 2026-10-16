import { isUuid } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { isAttendanceAdjustmentCursor } from "../policies/attendance-adjustment-policy";
import { canReadWorkPeriodSnapshot, isWorkPeriodMonth } from "../policies/work-period-policy";
import type { AttendanceAdjustmentRepository } from "../repositories/attendance-adjustment-repository";
import type { WorkPeriodRepository } from "../repositories/work-period-repository";

export class LoadAttendanceAdjustments {
  constructor(
    private readonly adjustments: AttendanceAdjustmentRepository,
    private readonly periods: WorkPeriodRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    month: string,
    before: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadWorkPeriodSnapshot(access.permissions)) return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    if (!isWorkPeriodMonth(month)) return failed("invalid_work_period_range");
    if (before !== null && !isAttendanceAdjustmentCursor(before)) return failed("invalid_page");
    const id = employee.toLowerCase() as EmployeeId;
    const snapshot = await this.periods.snapshot(access.companyId, month, id, signal);
    signal.throwIfAborted();
    if (!snapshot.ok) return snapshot;
    if (!snapshot.value.adjustmentsSupported) return failed("attendance_adjustments_unavailable");
    const history = await this.adjustments.history(access.companyId, id, month, before, signal);
    signal.throwIfAborted();
    return history.ok
      ? success(
          Object.freeze({
            employee: snapshot.value.employee,
            period: snapshot.value.period,
            ...history.value,
          }),
        )
      : history;
  }
}
