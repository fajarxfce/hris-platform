import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { CalendarRange } from "../entities/calendar-range";
import type { CalendarReview } from "../entities/calendar-review";
import { canReadCalendars, parseCalendarRange } from "../policies/calendar-policy";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class LoadCalendarReview {
  constructor(private readonly calendars: CalendarRepository) {}
  execute(
    access: CompanyAccess,
    employeeId: string,
    input: CalendarRange,
    signal: AbortSignal,
  ): Promise<Result<CalendarReview>> {
    signal.throwIfAborted();
    if (!canReadCalendars(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employeeId)) return Promise.resolve(failed("employee_not_found"));
    const range = parseCalendarRange(input);
    return range.ok
      ? this.calendars.review(
          access.companyId,
          employeeId.toLowerCase() as EmployeeId,
          range.value,
          signal,
        )
      : Promise.resolve(range);
  }
}
