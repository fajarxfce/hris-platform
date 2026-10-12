import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceCorrectionHistory } from "../entities/attendance-correction-history";
import { canReadAttendance, isAttendanceCorrectionCursor } from "../policies/attendance-policy";
import type { AttendanceRepository } from "../repositories/attendance-repository";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class LoadAttendanceCorrectionHistory {
  constructor(
    private readonly attendance: AttendanceRepository,
    private readonly calendars: CalendarRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    workDate: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<AttendanceCorrectionHistory>> {
    signal.throwIfAborted();
    if (!canReadAttendance(access.permissions)) return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    if (!isCalendarDate(workDate)) return failed("invalid_attendance_range");
    if (after !== null && !isAttendanceCorrectionCursor(after)) return failed("invalid_page");
    const id = employee.toLowerCase() as EmployeeId;
    const reference = await this.calendars.review(
      access.companyId,
      id,
      { from: workDate, until: workDate },
      signal,
    );
    signal.throwIfAborted();
    if (!reference.ok) return reference;
    const history = await this.attendance.corrections(
      access.companyId,
      id,
      workDate,
      after,
      signal,
    );
    signal.throwIfAborted();
    return history.ok
      ? success(Object.freeze({ ...history.value, employee: reference.value.employee }))
      : history;
  }
}
