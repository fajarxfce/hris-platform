import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OvertimeReport } from "../../domain/entities/overtime-report";
import { reportingMessages } from "../i18n/reporting-messages";

export function overtimeReportView(report: OvertimeReport, locale: Locale) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const month = new Intl.DateTimeFormat(locale, {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
  const keys = [
    "requests",
    "employments",
    "actualRequests",
    "requestedMinutes",
    "actualMinutes",
    "approvedMinutes",
  ] as const;
  return {
    metrics: keys.map((key) => ({
      key,
      label: text[key],
      value: number.format(report.totals[key]),
    })),
    rows: report.months.map((row) => ({
      id: row.month,
      actionLabel: month.format(new Date(`${row.month}-01T00:00:00Z`)),
      actionDisabled: row.counts.requests === 0,
      cells: [
        month.format(new Date(`${row.month}-01T00:00:00Z`)),
        ...keys.map((key) => number.format(row.counts[key])),
      ],
    })),
  };
}
