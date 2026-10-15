import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type LeaveCalendarScopeDto,
  leaveCalendarDto,
  leaveCalendarEntriesDto,
} from "../models/leave-calendar-dto";
import type { LeaveCalendarDataSource } from "./leave-calendar-data-source";

export class HttpLeaveCalendarDataSource implements LeaveCalendarDataSource {
  constructor(private readonly http: HttpClient) {}
  async month(
    company: string,
    scope: LeaveCalendarScopeDto,
    month: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ scope });
    if (month !== null) query.set("month", month);
    return leaveCalendarDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/calendar?${query}` },
        signal,
      ),
    );
  }
  async entries(
    company: string,
    scope: LeaveCalendarScopeDto,
    date: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ scope, date, limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveCalendarEntriesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/calendar/entries?${query}` },
        signal,
      ),
    );
  }
}
