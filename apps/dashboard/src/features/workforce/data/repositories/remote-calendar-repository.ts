import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { RosterDayChange } from "../../domain/entities/roster-day-change";
import type { WeeklyScheduleChange } from "../../domain/entities/weekly-schedule-change";
import type { WorkforceEmployeeSearch } from "../../domain/entities/workforce-employee-search";
import type { CalendarRepository } from "../../domain/repositories/calendar-repository";
import type { CalendarDataSource } from "../datasources/calendar-data-source";
import {
  toCalendarReceipt,
  toRosterDayDto,
  toWeeklyScheduleDto,
} from "../mappers/calendar-change-mapper";
import { toCalendarReview } from "../mappers/calendar-review-mapper";
import { toWorkforceEmployeePage } from "../mappers/workforce-employee-mapper";

export class RemoteCalendarRepository implements CalendarRepository {
  constructor(private readonly source: CalendarDataSource) {}
  employees(company: CompanyId, search: WorkforceEmployeeSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toWorkforceEmployeePage(
        await this.source.employees(company, search, signal),
        company,
        search.after,
      ),
    );
  }
  review(company: CompanyId, employee: EmployeeId, range: CalendarRange, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toCalendarReview(
        await this.source.review(company, employee, range, signal),
        company,
        employee,
        range,
      ),
    );
  }
  assign(
    company: CompanyId,
    operation: OperationId,
    change: WeeklyScheduleChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toCalendarReceipt(
        await this.source.assign(
          company,
          change.employeeId,
          operation,
          toWeeklyScheduleDto(change),
          signal,
        ),
        change.employeeId,
        change.expectedVersion,
      ),
    );
  }
  roster(company: CompanyId, operation: OperationId, change: RosterDayChange, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toCalendarReceipt(
        await this.source.roster(
          company,
          change.employeeId,
          change.workDate,
          operation,
          toRosterDayDto(change),
          signal,
        ),
        change.employeeId,
        change.expectedVersion,
      ),
    );
  }
}
