import type {
  CalendarRangeDto,
  CalendarReceiptDto,
  CalendarReviewDto,
  RosterDayChangeDto,
  RosterResetDto,
  WeeklyScheduleChangeDto,
  WorkforceEmployeePageDto,
  WorkforceEmployeeSearchDto,
} from "../models/calendar-dto";
import type {
  RosterBulkApplyDto,
  RosterBulkReviewDto,
  RosterBulkSelectionDto,
} from "../models/roster-bulk-dto";

export interface CalendarDataSource {
  reviewBulk(
    company: string,
    selection: RosterBulkSelectionDto,
    signal: AbortSignal,
  ): Promise<RosterBulkReviewDto>;
  applyBulk(
    company: string,
    operation: string,
    body: RosterBulkApplyDto,
    signal: AbortSignal,
  ): Promise<CalendarReceiptDto>;
  employees(
    company: string,
    search: WorkforceEmployeeSearchDto,
    signal: AbortSignal,
  ): Promise<WorkforceEmployeePageDto>;
  review(
    company: string,
    employee: string,
    range: CalendarRangeDto,
    signal: AbortSignal,
  ): Promise<CalendarReviewDto>;
  assign(
    company: string,
    employee: string,
    operation: string,
    body: WeeklyScheduleChangeDto,
    signal: AbortSignal,
  ): Promise<CalendarReceiptDto>;
  roster(
    company: string,
    employee: string,
    day: string,
    operation: string,
    body: RosterDayChangeDto,
    signal: AbortSignal,
  ): Promise<CalendarReceiptDto>;
  resetRoster(
    company: string,
    employee: string,
    day: string,
    operation: string,
    body: RosterResetDto,
    signal: AbortSignal,
  ): Promise<CalendarReceiptDto>;
}
