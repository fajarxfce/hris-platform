import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { LeaveCalendar, LeaveCalendarScope } from "../entities/leave-calendar";
import type { LeaveCalendarEntries } from "../entities/leave-calendar-entry";

export interface LeaveCalendarRepository {
  month(
    company: CompanyId,
    scope: LeaveCalendarScope,
    month: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveCalendar>>;
  entries(
    company: CompanyId,
    scope: LeaveCalendarScope,
    date: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveCalendarEntries>>;
}
