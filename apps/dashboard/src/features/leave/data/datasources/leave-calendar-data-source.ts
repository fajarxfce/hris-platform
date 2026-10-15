import type {
  LeaveCalendarDto,
  LeaveCalendarEntriesDto,
  LeaveCalendarScopeDto,
} from "../models/leave-calendar-dto";

export interface LeaveCalendarDataSource {
  month(
    company: string,
    scope: LeaveCalendarScopeDto,
    month: string | null,
    signal: AbortSignal,
  ): Promise<LeaveCalendarDto>;
  entries(
    company: string,
    scope: LeaveCalendarScopeDto,
    date: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveCalendarEntriesDto>;
}
