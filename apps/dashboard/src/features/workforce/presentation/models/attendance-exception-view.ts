import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AttendanceExceptionPage } from "../../domain/entities/attendance-exception-page";
import { attendanceMessages } from "../i18n/attendance-messages";

export function attendanceExceptionRows(
  page: AttendanceExceptionPage,
  locale: Locale,
  timezone: string,
) {
  const text = attendanceMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  const instant = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  return page.items.map((item) => ({
    id: `${item.employeeId}:${item.workDate}`,
    actionLabel: `${item.employeeNumber}, ${date.format(new Date(`${item.workDate}T00:00:00Z`))}`,
    cells: [
      date.format(new Date(`${item.workDate}T00:00:00Z`)),
      `${item.employeeName} · ${item.employeeNumber}`,
      number.format(item.pendingCount),
      item.incomplete ? text.incomplete : item.correctionVersion !== null ? text.corrected : "—",
      instant.format(new Date(item.lastReceivedAt)),
    ],
  }));
}
