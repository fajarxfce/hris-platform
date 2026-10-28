import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  AttendanceClosingEmployeePage,
  AttendanceClosingReport,
} from "../../domain/entities/attendance-closing-report";
import { attendanceClosingReportMessages } from "../i18n/attendance-closing-report-messages";
export function attendanceClosingReportView(report: AttendanceClosingReport, locale: Locale) {
  const text = attendanceClosingReportMessages(locale),
    n = new Intl.NumberFormat(locale),
    month = new Intl.DateTimeFormat(locale, { month: "long", year: "numeric", timeZone: "UTC" });
  const keys = [
    "closedMonths",
    "employmentMonths",
    "employments",
    "adjustedEmploymentMonths",
  ] as const;
  const facts = [
    "workedDays",
    "absenceDays",
    "unrecordedDays",
    "offDays",
    "unassignedDays",
    "acceptedMinutes",
  ] as const;
  const common = ["days", "scheduledDays", "scheduledMinutes"] as const;
  return {
    metrics: keys.map((key) => ({ key, label: text[key], value: n.format(report.totals[key]) })),
    comparisons: [
      ...common.map((key) => ({
        id: key,
        cells: [text[key], n.format(report.totals[key]), n.format(report.totals[key])],
      })),
      ...facts.map((key) => ({
        id: key,
        cells: [
          text[key],
          n.format(report.totals.original[key]),
          n.format(report.totals.current[key]),
        ],
      })),
    ],
    rows: report.months.map((row) => ({
      id: row.month,
      actionLabel: month.format(new Date(`${row.month}-01T00:00:00Z`)),
      actionDisabled: row.counts.employments === 0,
      cells: [
        month.format(new Date(`${row.month}-01T00:00:00Z`)),
        row.closing ? text.closed : text.unclosed,
        n.format(row.counts.employments),
        n.format(row.counts.adjustedEmploymentMonths),
        n.format(row.counts.original.acceptedMinutes),
        n.format(row.counts.current.acceptedMinutes),
        n.format(row.counts.original.absenceDays),
        n.format(row.counts.current.absenceDays),
      ],
    })),
  };
}
export function attendanceClosingEmployeesView(
  page: AttendanceClosingEmployeePage,
  locale: Locale,
) {
  const text = attendanceClosingReportMessages(locale),
    n = new Intl.NumberFormat(locale);
  return page.items.map((row) => ({
    id: row.employeeId,
    actionLabel: `${row.employeeNumber} · ${row.employeeName}`,
    cells: [
      row.employeeNumber,
      row.employeeName,
      row.adjustment ? n.format(row.adjustment.revision) : text.original,
      n.format(row.counts.original.acceptedMinutes),
      n.format(row.counts.current.acceptedMinutes),
      n.format(row.counts.original.absenceDays),
      n.format(row.counts.current.absenceDays),
    ],
  }));
}
