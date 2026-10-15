import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveCalendarScope } from "../entities/leave-calendar";
import { canReadLeaveCalendar, isLeaveCalendarDate } from "../policies/leave-calendar-policy";
import type { LeaveCalendarRepository } from "../repositories/leave-calendar-repository";

export class LoadLeaveCalendarEntries {
  constructor(private readonly calendar: LeaveCalendarRepository) {}
  execute(
    access: CompanyAccess,
    scope: LeaveCalendarScope,
    date: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadLeaveCalendar(access.permissions, scope))
      return Promise.resolve(failed("access_denied"));
    if (!isLeaveCalendarDate(date)) return Promise.resolve(failed("invalid_leave_calendar_date"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.calendar.entries(
      access.companyId,
      scope,
      date,
      after?.toLowerCase() ?? null,
      signal,
    );
  }
}
