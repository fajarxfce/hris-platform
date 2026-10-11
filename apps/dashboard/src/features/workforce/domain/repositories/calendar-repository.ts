import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { CalendarRange } from "../entities/calendar-range";
import type { CalendarReview } from "../entities/calendar-review";
import type { RosterDayChange } from "../entities/roster-day-change";
import type { WeeklyScheduleChange } from "../entities/weekly-schedule-change";
import type { WorkforceEmployeePage } from "../entities/workforce-employee-page";
import type { WorkforceEmployeeSearch } from "../entities/workforce-employee-search";

export interface CalendarRepository {
  employees(
    company: CompanyId,
    search: WorkforceEmployeeSearch,
    signal: AbortSignal,
  ): Promise<Result<WorkforceEmployeePage>>;
  review(
    company: CompanyId,
    employeeId: EmployeeId,
    range: CalendarRange,
    signal: AbortSignal,
  ): Promise<Result<CalendarReview>>;
  assign(
    company: CompanyId,
    operation: OperationId,
    change: WeeklyScheduleChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  roster(
    company: CompanyId,
    operation: OperationId,
    change: RosterDayChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
