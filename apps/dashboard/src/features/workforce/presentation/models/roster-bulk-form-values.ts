import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { RosterBulkMode } from "../../domain/entities/roster-bulk-selection";
import { type Weekday, weekdays } from "../../domain/entities/schedule-assignment";
import type { ShiftSnapshot } from "../../domain/entities/shift-snapshot";
import type { WorkforceEmployee } from "../../domain/entities/workforce-employee";

export type RosterBulkFormValues = {
  from: string;
  until: string;
  weekdays: Weekday[];
  mode: RosterBulkMode;
  shift: ShiftSnapshot | null;
  employees: WorkforceEmployee[];
  reason: string;
};

/** Convert bounded date/weekday controls into explicit work dates, without a local timezone. */
export function rosterBulkDates(
  from: string,
  until: string,
  selected: readonly Weekday[],
): readonly string[] | null {
  if (!isCalendarDate(from) || !isCalendarDate(until) || from < "1900-01-01" || until < from)
    return null;
  const start = Date.parse(`${from}T00:00:00Z`);
  const count = (Date.parse(`${until}T00:00:00Z`) - start) / 86_400_000 + 1;
  if (
    count > 31 ||
    selected.length === 0 ||
    selected.some((day) => !weekdays.includes(day)) ||
    new Set(selected).size !== selected.length
  )
    return null;
  const dates: string[] = [];
  for (let index = 0; index < count; index++) {
    const day = new Date(start + index * 86_400_000);
    if (selected.includes(weekdays[(day.getUTCDay() + 6) % 7] as Weekday))
      dates.push(day.toISOString().slice(0, 10));
  }
  return Object.freeze(dates);
}

export function initialRosterBulkValues(today: string): RosterBulkFormValues {
  const from = isCalendarDate(today) && today >= "1900-01-01" ? today : "1900-01-01";
  const until = new Date(
    Math.min(Date.parse(`${from}T00:00:00Z`) + 6 * 86_400_000, Date.parse("9999-12-31T00:00:00Z")),
  )
    .toISOString()
    .slice(0, 10);
  return {
    from,
    until,
    weekdays: [...weekdays],
    mode: "DAY_OFF",
    shift: null,
    employees: [],
    reason: "",
  };
}
