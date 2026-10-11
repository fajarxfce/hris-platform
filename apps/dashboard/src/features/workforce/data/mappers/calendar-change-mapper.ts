import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { RosterDayChange } from "../../domain/entities/roster-day-change";
import type { WeeklyScheduleChange } from "../../domain/entities/weekly-schedule-change";
import type {
  CalendarReceiptDto,
  RosterDayChangeDto,
  WeeklyScheduleChangeDto,
} from "../models/calendar-dto";

export function toWeeklyScheduleDto(change: WeeklyScheduleChange): WeeklyScheduleChangeDto {
  return {
    effectiveFrom: change.effectiveFrom,
    days: change.days,
    expectedVersion: change.expectedVersion,
    reason: change.reason,
  };
}
export function toRosterDayDto(change: RosterDayChange): RosterDayChangeDto {
  return { shift: change.shift, expectedVersion: change.expectedVersion, reason: change.reason };
}
export function toCalendarReceipt(
  dto: CalendarReceiptDto,
  employeeId: string,
  expectedVersion: number | null,
): MutationReceipt {
  if (
    dto.id.toLowerCase() !== employeeId ||
    dto.version !== (expectedVersion === null ? 0 : expectedVersion + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
