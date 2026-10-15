import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveCalendarScope } from "../entities/leave-calendar";
import { canReadLeaveCalendar, isLeaveCalendarMonth } from "../policies/leave-calendar-policy";
import type { LeaveCalendarRepository } from "../repositories/leave-calendar-repository";

export class LoadLeaveCalendar {
  constructor(private readonly calendar: LeaveCalendarRepository) {}
  execute(
    access: CompanyAccess,
    scope: LeaveCalendarScope,
    month: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadLeaveCalendar(access.permissions, scope))
      return Promise.resolve(failed("access_denied"));
    if (month !== null && !isLeaveCalendarMonth(month))
      return Promise.resolve(failed("invalid_leave_calendar_month"));
    return this.calendar.month(access.companyId, scope, month, signal);
  }
}
