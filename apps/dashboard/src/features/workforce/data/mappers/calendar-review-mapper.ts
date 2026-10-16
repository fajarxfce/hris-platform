import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { CalendarReview } from "../../domain/entities/calendar-review";
import type { RosterOverride } from "../../domain/entities/roster-override";
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
  // Older servers expose only active overrides through the resolved day.
  const overrides: readonly RosterOverride[] =
    dto.rosterOverrides === undefined
      ? days.flatMap((day) =>
          day.kind !== "UNASSIGNED" && day.origin === "ROSTER"
            ? [
                Object.freeze({
                  workDate: day.workDate,
                  version: day.originVersion,
                  active: true,
                  shift: day.kind === "WORK" ? day.shift : null,
                }),
              ]
            : [],
        )
      : dto.rosterOverrides.map((row) => {
          if (
            !isCalendarDate(row.workDate) ||
            row.workDate < range.from ||
            row.workDate > range.until ||
            (!row.active && row.shift !== null)
          )
            throw new InvalidHttpResponseError();
          return Object.freeze({
            ...row,
            shift: row.shift === null ? null : toShiftSnapshot(row.shift),
          });
        });
  const byDate = new Map(overrides.map((row) => [row.workDate, row]));
  if (
    byDate.size !== overrides.length ||
    overrides.some(
      (row, index) => index > 0 && row.workDate <= (overrides[index - 1]?.workDate ?? ""),
    )
  )
    throw new InvalidHttpResponseError();
  for (const day of days) {
    const override = byDate.get(day.workDate);
    if (override?.active) {
      if (
        day.kind === "UNASSIGNED" ||
        day.origin !== "ROSTER" ||
        day.originVersion !== override.version ||
        JSON.stringify(day.kind === "WORK" ? day.shift : null) !== JSON.stringify(override.shift)
      )
        throw new InvalidHttpResponseError();
    } else if (day.kind !== "UNASSIGNED" && day.origin === "ROSTER")
      throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    employee,
    range: Object.freeze({ ...range }),
    scheduleVersion: version,
    days: Object.freeze(days),
    rosterOverrides: Object.freeze(overrides),
    supportsRosterReset: dto.rosterOverrides !== undefined,
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
