import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AttendanceCorrectionHistory } from "../../domain/entities/attendance-correction-history";
import { attendanceHistoryMessages } from "../i18n/attendance-history-messages";
import { calendarMessages } from "../i18n/calendar-messages";
import { attendanceCorrectionView } from "./attendance-view";

export function attendanceCorrectionHistoryView(
  history: AttendanceCorrectionHistory,
  selected: string | null,
  locale: Locale,
  timezone: string,
) {
  const text = attendanceHistoryMessages(locale),
    calendar = calendarMessages(locale),
    number = new Intl.NumberFormat(locale),
    date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" }),
    instant = new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
  const change = history.items.find((item) => String(item.version) === selected);
  return {
    properties: [
      { label: calendar.name, value: history.employee.legalName },
      { label: calendar.number, value: history.employee.employeeNumber },
      { label: calendar.date, value: date.format(new Date(`${history.workDate}T00:00:00Z`)) },
      { label: calendar.timezone, value: timezone },
    ],
    rows: history.items.map((item) => ({
      id: String(item.version),
      cells: [
        number.format(item.version),
        instant.format(new Date(item.recordedAt)),
        item.clockIn === null ? text.absence : text.workingTime,
        item.reason,
      ],
    })),
    change: change
      ? [
          { label: text.reference, value: change.id },
          { label: text.revision, value: number.format(change.version) },
          { label: text.type, value: change.clockIn === null ? text.absence : text.workingTime },
          { label: calendar.timezone, value: timezone },
          ...attendanceCorrectionView(change, locale, timezone),
        ]
      : null,
  };
}
