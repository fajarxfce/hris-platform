import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { WorkHoliday } from "../../domain/entities/work-holiday";
import { holidayMessages } from "../i18n/holiday-messages";

export function holidayDirectoryView(holidays: readonly WorkHoliday[], locale: Locale) {
  const text = holidayMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  return holidays.map((holiday) => ({
    id: holiday.id,
    actionLabel: `${holiday.name} (${holiday.workDate})`,
    cells: [
      date.format(new Date(`${holiday.workDate}T00:00:00Z`)),
      holiday.name,
      holiday.active ? text.active : text.inactive,
    ],
  }));
}
export function holidayView(holiday: WorkHoliday, locale: Locale) {
  const text = holidayMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "long" });
  return [
    { label: text.date, value: date.format(new Date(`${holiday.workDate}T00:00:00Z`)) },
    { label: text.name, value: holiday.name },
    { label: text.status, value: holiday.active ? text.active : text.inactive },
    { label: text.version, value: new Intl.NumberFormat(locale).format(holiday.version) },
  ];
}
