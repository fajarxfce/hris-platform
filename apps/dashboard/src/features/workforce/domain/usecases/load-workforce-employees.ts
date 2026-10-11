import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceEmployeePage } from "../entities/workforce-employee-page";
import type { WorkforceEmployeeSearch } from "../entities/workforce-employee-search";
import { canReadCalendars, parseWorkforceEmployeeSearch } from "../policies/calendar-policy";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class LoadWorkforceEmployees {
  constructor(private readonly calendars: CalendarRepository) {}
  execute(
    access: CompanyAccess,
    input: WorkforceEmployeeSearch,
    signal: AbortSignal,
  ): Promise<Result<WorkforceEmployeePage>> {
    signal.throwIfAborted();
    if (!canReadCalendars(access.permissions)) return Promise.resolve(failed("access_denied"));
    const search = parseWorkforceEmployeeSearch(input);
    return search.ok
      ? this.calendars.employees(access.companyId, search.value, signal)
      : Promise.resolve(search);
  }
}
