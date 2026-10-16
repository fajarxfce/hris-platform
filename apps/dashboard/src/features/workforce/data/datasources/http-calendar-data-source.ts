import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type CalendarRangeDto,
  calendarReceiptDto,
  calendarReviewDto,
  type RosterDayChangeDto,
  type RosterResetDto,
  type WeeklyScheduleChangeDto,
  type WorkforceEmployeeSearchDto,
  workforceEmployeePageDto,
} from "../models/calendar-dto";
import type { CalendarDataSource } from "./calendar-data-source";

export class HttpCalendarDataSource implements CalendarDataSource {
  constructor(private readonly http: HttpClient) {}
  async employees(company: string, search: WorkforceEmployeeSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ asOf: search.asOf, query: search.query, limit: "50" });
    if (search.after !== null) query.set("after", search.after);
    return workforceEmployeePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/employees?${query}` },
        signal,
      ),
    );
  }
  async review(company: string, employee: string, range: CalendarRangeDto, signal: AbortSignal) {
    const query = new URLSearchParams({ from: range.from, until: range.until });
    return calendarReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/calendar/review?${query}`,
        },
        signal,
      ),
    );
  }
  async assign(
    company: string,
    employee: string,
    operation: string,
    body: WeeklyScheduleChangeDto,
    signal: AbortSignal,
  ) {
    return calendarReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/schedule`,
          method: "PUT",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async resetRoster(
    company: string,
    employee: string,
    day: string,
    operation: string,
    body: RosterResetDto,
    signal: AbortSignal,
  ) {
    return calendarReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/roster/${day}/reset`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async roster(
    company: string,
    employee: string,
    day: string,
    operation: string,
    body: RosterDayChangeDto,
    signal: AbortSignal,
  ) {
    return calendarReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/employees/${employee}/roster/${day}`,
          method: "PUT",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
