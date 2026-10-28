import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AttendanceExceptionReport } from "../../domain/entities/attendance-exception-report";
import { reportingMessages } from "../i18n/reporting-messages";

export function attendanceReportView(report: AttendanceExceptionReport, locale: Locale) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return {
    metrics: (
      [
        "affectedDays",
        "affectedEmployments",
        "pendingDays",
        "incompleteDays",
        "pendingEvents",
        "observedDays",
      ] as const
    ).map((key) => ({ key, label: text[key], value: number.format(report.totals[key]) })),
    rows: report.days.map((day) => ({
      id: day.date,
      actionLabel: date.format(new Date(`${day.date}T00:00:00Z`)),
      actionDisabled: day.counts.affectedDays === 0,
      cells: [
        date.format(new Date(`${day.date}T00:00:00Z`)),
        number.format(day.counts.affectedDays),
        number.format(day.counts.pendingDays),
        number.format(day.counts.incompleteDays),
      ],
    })),
  };
}
