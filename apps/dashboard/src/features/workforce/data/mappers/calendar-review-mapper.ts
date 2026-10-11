import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { CalendarReview } from "../../domain/entities/calendar-review";
import type { CalendarReviewDto } from "../models/calendar-dto";
import { toScheduledDay, toShiftSnapshot } from "./scheduled-day-mapper";
import { toWorkforceEmployee } from "./workforce-employee-mapper";

export function toCalendarReview(
  dto: CalendarReviewDto,
  company: CompanyId,
  employeeId: EmployeeId,
  range: CalendarRange,
): CalendarReview {
  const employee = toWorkforceEmployee(dto.employee, company);
  const first = Date.parse(`${range.from}T00:00:00Z`);
  const count = (Date.parse(`${range.until}T00:00:00Z`) - first) / 86_400_000 + 1;
  const assignment = dto.assignmentAtStart;
  const version = dto.calendar.scheduleVersion;
  if (
    employee.id !== employeeId ||
    dto.from !== range.from ||
    dto.until !== range.until ||
    dto.calendar.days.length !== count ||
    (assignment !== null &&
      (!isCalendarDate(assignment.effectiveFrom) ||
        assignment.effectiveFrom > range.from ||
        version === null ||
        assignment.revision > version))
  )
    throw new InvalidHttpResponseError();
  const days = dto.calendar.days.map((day, index) =>
    toScheduledDay(day, new Date(first + index * 86_400_000).toISOString().slice(0, 10), version),
  );
  const firstDay = days[0];
  if (
    firstDay?.kind !== "UNASSIGNED" &&
    firstDay?.origin === "PATTERN" &&
    (!assignment || firstDay.originVersion !== assignment.revision)
  )
    throw new InvalidHttpResponseError();
  if (assignment !== null && firstDay?.kind === "UNASSIGNED") throw new InvalidHttpResponseError();
  return Object.freeze({
    employee,
    range: Object.freeze({ ...range }),
    scheduleVersion: version,
    days: Object.freeze(days),
    assignmentAtStart:
      assignment === null
        ? null
        : Object.freeze({
            effectiveFrom: assignment.effectiveFrom,
            revision: assignment.revision,
            days: Object.freeze(
              Object.fromEntries(
                Object.entries(assignment.days).map(([day, shift]) => [
                  day,
                  toShiftSnapshot(shift),
                ]),
              ),
            ),
          }),
  });
}
