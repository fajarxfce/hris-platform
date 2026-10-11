import type { EmployeeId } from "../../people/domain/entities/employee";
import { toCalendarReview } from "../data/mappers/calendar-review-mapper";
import type {
  CalendarReviewDto,
  ScheduledDayDto,
  WorkforceEmployeeDto,
} from "../data/models/calendar-dto";
import type { CalendarRange } from "../domain/entities/calendar-range";
import { weekdays } from "../domain/entities/schedule-assignment";
import { company, identifier, shiftDtoFixture } from "./workforce-fixture";

export {
  access,
  company,
  identifier,
  operation,
  shiftDtoFixture,
  shiftFixture,
  signal,
} from "./workforce-fixture";
export const employeeId = identifier(501) as EmployeeId;
export const calendarRangeFixture = { from: "2026-10-12", until: "2026-10-18" };
export const employeeDtoFixture = (): WorkforceEmployeeDto => ({
  id: employeeId,
  employeeNumber: "EMP-001",
  legalName: "Calendar Employee",
  status: "ACTIVE",
  version: 3,
});
export function calendarDtoFixture(range: CalendarRange = calendarRangeFixture): CalendarReviewDto {
  const shift = shiftDtoFixture();
  const snapshot = { id: shift.id, revision: 0, details: shift.details };
  const from = Date.parse(`${range.from}T00:00:00Z`);
  const count = (Date.parse(`${range.until}T00:00:00Z`) - from) / 86_400_000 + 1;
  return {
    employee: employeeDtoFixture(),
    ...range,
    assignmentAtStart: {
      effectiveFrom: "2026-01-01",
      revision: 2,
      days: Object.fromEntries(weekdays.map((day) => [day, snapshot])),
    },
    calendar: {
      scheduleVersion: 4,
      days: Array.from(
        { length: count },
        (_, index): ScheduledDayDto => ({
          workDate: new Date(from + index * 86_400_000).toISOString().slice(0, 10),
          kind: "WORK",
          shift: snapshot,
          startsAt: new Date(from + index * 86_400_000 + 15 * 3_600_000).toISOString(),
          endsAt: new Date(from + index * 86_400_000 + 23 * 3_600_000).toISOString(),
          plannedMinutes: 450,
          origin: "PATTERN",
          originVersion: 2,
          holidayId: null,
        }),
      ),
    },
  };
}
export const calendarFixture = (range: CalendarRange = calendarRangeFixture) =>
  toCalendarReview(calendarDtoFixture(range), company, employeeId, range);
