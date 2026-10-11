import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { CalendarRange } from "../entities/calendar-range";
import type { EmployeeAttendanceReview } from "../entities/employee-attendance-review";
import { canReadAttendance, parseAttendanceRange } from "../policies/attendance-policy";
import type { AttendanceRepository } from "../repositories/attendance-repository";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class LoadEmployeeAttendance {
  constructor(
    private readonly attendance: AttendanceRepository,
    private readonly calendars: CalendarRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    input: CalendarRange,
    signal: AbortSignal,
  ): Promise<Result<EmployeeAttendanceReview>> {
    signal.throwIfAborted();
    if (!canReadAttendance(access.permissions)) return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    const range = parseAttendanceRange(input);
    if (!range.ok) return range;
    const id = employee.toLowerCase() as EmployeeId;
    const reference = await this.calendars.review(
      access.companyId,
      id,
      { from: range.value.from, until: range.value.from },
      signal,
    );
    signal.throwIfAborted();
    if (!reference.ok) return reference;
    const attendance = await this.attendance.days(access.companyId, id, range.value, signal);
    signal.throwIfAborted();
    return attendance.ok
      ? success(Object.freeze({ ...attendance.value, employee: reference.value.employee }))
      : attendance;
  }
}
