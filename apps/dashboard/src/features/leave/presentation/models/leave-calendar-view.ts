import type { MonthCalendarWeek } from "../../../../core/presentation/components/app-month-calendar";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeaveCalendar } from "../../domain/entities/leave-calendar";
import type { LeaveCalendarEntry } from "../../domain/entities/leave-calendar-entry";
import {
  isLeaveCalendarDate,
  isLeaveCalendarMonth,
} from "../../domain/policies/leave-calendar-policy";
import { leaveCalendarMessages } from "../i18n/leave-calendar-messages";
import { leaveMessages } from "../i18n/leave-messages";

export function adjacentLeaveCalendarMonth(month: string, offset: -1 | 1): string | null {
  if (!isLeaveCalendarMonth(month)) return null;
  const date = new Date(`${month}-01T00:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() + offset);
  const result = date.toISOString().slice(0, 7);
  return isLeaveCalendarMonth(result) ? result : null;
}
export function leaveCalendarView(
  calendar: LeaveCalendar,
  selected: string | null,
  locale: Locale,
) {
  const text = leaveCalendarMessages(locale),
    status = leaveMessages(locale);
  const fullDate = new Intl.DateTimeFormat(locale, { dateStyle: "full", timeZone: "UTC" }),
    monthFormat = new Intl.DateTimeFormat(locale, {
      year: "numeric",
      month: "long",
      timeZone: "UTC",
    }),
    weekday = new Intl.DateTimeFormat(locale, { weekday: "short", timeZone: "UTC" }),
    number = new Intl.NumberFormat(locale);
  const first = new Date(`${calendar.month}-01T00:00:00Z`),
    offset = (first.getUTCDay() + 6) % 7;
  const final = new Date(first);
  final.setUTCMonth(final.getUTCMonth() + 1);
  final.setUTCDate(0);
  const length = final.getUTCDate(),
    summaries = new Map(calendar.days.map((day) => [day.date, day]));
  const count = Math.ceil((offset + length) / 7);
  const weeks: MonthCalendarWeek[] = Array.from({ length: count }, (_, week) => ({
    id: `${calendar.month}-week-${week}`,
    days: Array.from({ length: 7 }, (_, index) => {
      const day = week * 7 + index - offset + 1;
      if (day < 1 || day > length)
        return {
          id: `blank-${week}-${index}`,
          date: null,
          number: "",
          label: "",
          count: null,
          selected: false,
          today: false,
        };
      const date = `${calendar.month}-${String(day).padStart(2, "0")}`,
        summary = summaries.get(date);
      const employees = summary?.employeeCount ?? 0;
      const label = `${fullDate.format(new Date(`${date}T00:00:00Z`))}, ${number.format(employees)} ${employees === 1 ? text.employee : text.employees}`;
      return {
        id: date,
        date,
        number: number.format(day),
        count: employees > 0 ? number.format(employees) : null,
        label: summary
          ? `${label}; ${status.PENDING}: ${number.format(summary.pendingCount)}; ${status.APPROVED}: ${number.format(summary.approvedCount)}; ${status.CANCELLATION_PENDING}: ${number.format(summary.cancellationPendingCount)}`
          : label,
        selected: date === selected,
        today: date === calendar.companyDate,
      };
    }),
  }));
  const weekdays = Array.from({ length: 7 }, (_, index) => ({
    id: String(index),
    label: weekday.format(new Date(Date.UTC(2026, 0, 5 + index))),
  }));
  const selectedSummary = summaries.get(selected ?? ""),
    employees = selectedSummary?.employeeCount ?? 0,
    requests = selectedSummary?.requestCount ?? 0;
  return {
    title: monthFormat.format(first),
    weeks,
    weekdays,
    previousMonth: adjacentLeaveCalendarMonth(calendar.month, -1),
    nextMonth: adjacentLeaveCalendarMonth(calendar.month, 1),
    selectedTitle:
      selected !== null &&
      isLeaveCalendarDate(selected) &&
      selected.startsWith(`${calendar.month}-`)
        ? fullDate.format(new Date(`${selected}T00:00:00Z`))
        : text.selectDate,
    counts: `${number.format(employees)} ${employees === 1 ? text.employee : text.employees} · ${number.format(requests)} ${requests === 1 ? text.request : text.requests}`,
  };
}
export function leaveCalendarRows(entries: readonly LeaveCalendarEntry[], locale: Locale) {
  const text = leaveMessages(locale);
  return entries.map((entry) => ({
    id: entry.requestId,
    actionLabel: entry.employeeName,
    cells: [
      entry.employeeName,
      entry.employeeNumber,
      entry.typeName,
      text[entry.portion],
      text[entry.status],
    ],
  }));
}
