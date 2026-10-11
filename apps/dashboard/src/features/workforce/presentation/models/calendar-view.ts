import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CalendarReview } from "../../domain/entities/calendar-review";
import { weekdays } from "../../domain/entities/schedule-assignment";
import type { ShiftDetails } from "../../domain/entities/shift-definition";
import type { WorkforceEmployeePage } from "../../domain/entities/workforce-employee-page";
import { calendarMessages } from "../i18n/calendar-messages";

export function shiftHours(details: ShiftDetails, locale: Locale): string {
  return `${details.startsAt}–${details.endsAt}${details.endsAt < details.startsAt ? ` (${calendarMessages(locale).nextDay})` : ""}`;
}
export function workforceEmployeeView(page: WorkforceEmployeePage, locale: Locale) {
  const text = calendarMessages(locale);
  return page.items.map((employee) => ({
    id: employee.id,
    actionLabel: `${employee.legalName} (${employee.employeeNumber})`,
    cells: [employee.employeeNumber, employee.legalName, text[employee.status]],
  }));
}
export function calendarView(review: CalendarReview, locale: Locale) {
  const text = calendarMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  const number = new Intl.NumberFormat(locale);
  return {
    days: review.days.map((day) => ({
      id: day.workDate,
      actionLabel: date.format(new Date(`${day.workDate}T00:00:00Z`)),
      cells: [
        date.format(new Date(`${day.workDate}T00:00:00Z`)),
        text[day.kind],
        day.kind === "WORK" ? day.shift.details.name : "—",
        day.kind === "WORK" ? shiftHours(day.shift.details, locale) : "—",
        day.kind === "WORK" ? day.shift.details.timezone : "—",
        day.kind === "WORK" ? number.format(day.plannedMinutes) : "—",
        day.kind === "UNASSIGNED" ? "—" : text[day.origin],
      ],
    })),
    pattern: review.assignmentAtStart
      ? weekdays.map((weekday) => {
          const shift = review.assignmentAtStart?.days[weekday];
          return {
            id: weekday,
            cells: [
              text[weekday],
              shift?.details.name ?? text.OFF,
              shift ? shiftHours(shift.details, locale) : "—",
              shift?.details.timezone ?? "—",
              shift ? number.format(shift.revision) : "—",
            ],
          };
        })
      : [],
    effectiveFrom: review.assignmentAtStart
      ? date.format(new Date(`${review.assignmentAtStart.effectiveFrom}T00:00:00Z`))
      : null,
  };
}
