import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { RosterBulkReview } from "../../domain/entities/roster-bulk-review";
import type { ScheduledDay } from "../../domain/entities/scheduled-day";
import type { WorkforceEmployee } from "../../domain/entities/workforce-employee";
import { calendarMessages } from "../i18n/calendar-messages";
import { rosterBulkMessages } from "../i18n/roster-bulk-messages";
import { shiftHours } from "./calendar-view";

export function rosterBulkView(review: RosterBulkReview, locale: Locale) {
  const text = rosterBulkMessages(locale),
    calendar = calendarMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const number = new Intl.NumberFormat(locale);
  return {
    properties: [
      { label: text.employees, value: number.format(review.employees.length) },
      { label: text.dates, value: number.format(review.selection.dates.length) },
      { label: text.changed, value: number.format(review.changeCount) },
    ],
    columns: [
      { id: "employee", label: calendar.name },
      { id: "date", label: calendar.date },
      { id: "before", label: text.before },
      { id: "after", label: text.after },
      { id: "status", label: text.status },
    ],
    rows: review.employees.flatMap((entry) =>
      entry.days.map((day) => ({
        id: `${entry.employee.id}:${day.workDate}`,
        cells: [
          `${entry.employee.legalName} · ${entry.employee.employeeNumber}`,
          date.format(new Date(`${day.workDate}T00:00:00Z`)),
          rosterScheduleLabel(day.before, locale),
          rosterScheduleLabel(day.after, locale),
          day.changed ? text.changed : text.unchanged,
        ],
      })),
    ),
  };
}

function rosterScheduleLabel(day: ScheduledDay, locale: Locale): string {
  const text = calendarMessages(locale);
  return day.kind === "WORK"
    ? `${day.shift.details.name} · ${shiftHours(day.shift.details, locale)} · ${day.shift.details.timezone}`
    : day.kind === "OFF"
      ? `${text.OFF} · ${text[day.origin]}`
      : text.UNASSIGNED;
}

/** Error context can identify only an employee already selected in this company. */
export function rosterBulkFailureContext(
  failure: Failure | null,
  employees: readonly WorkforceEmployee[],
  locale: Locale,
): string | null {
  const employee = employees.find((value) => value.id === failure?.parameters.employeeId);
  const date = failure?.parameters.workDate;
  if (!employee || !date || !isCalendarDate(date)) return null;
  const formatted = new Intl.DateTimeFormat(locale, {
    timeZone: "UTC",
    dateStyle: "medium",
  }).format(new Date(`${date}T00:00:00Z`));
  return `${employee.legalName} · ${formatted}`;
}
