import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { CalendarRange } from "../entities/calendar-range";
import type { CalendarReview } from "../entities/calendar-review";
import type { RosterBulkChange } from "../entities/roster-bulk-change";
import type { RosterBulkReview } from "../entities/roster-bulk-review";
import type { RosterBulkSelection } from "../entities/roster-bulk-selection";
import type { RosterDayChange } from "../entities/roster-day-change";
import type { RosterReset } from "../entities/roster-reset";
import type { WeeklyScheduleChange } from "../entities/weekly-schedule-change";
import type { WorkforceEmployeePage } from "../entities/workforce-employee-page";
import type { WorkforceEmployeeSearch } from "../entities/workforce-employee-search";

export interface CalendarRepository {
  reviewBulk(
    company: CompanyId,
    selection: RosterBulkSelection,
    signal: AbortSignal,
  ): Promise<Result<RosterBulkReview>>;
  applyBulk(
    company: CompanyId,
    operation: OperationId,
    change: RosterBulkChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
  resetRoster(
    company: CompanyId,
    operation: OperationId,
    change: RosterReset,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
