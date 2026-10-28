import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeaveReport } from "../../domain/entities/leave-report";
import type { LeaveReportRequestPage } from "../../domain/entities/leave-report-request";
import { reportingMessages } from "../i18n/reporting-messages";

export function formatLeaveReportDays(days: string, locale: Locale): string {
  const [whole = "0", half] = days.split(".");
  return (
    new Intl.NumberFormat(locale).format(BigInt(whole)) +
    (half ? `${locale === "id" ? "," : "."}5` : "")
  );
}

export function leaveReportView(report: LeaveReport, locale: Locale) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return {
    metrics: [
      { key: "requests", label: text.leaveRequests, value: number.format(report.totals.requests) },
      {
        key: "employments",
        label: text.employments,
        value: number.format(report.totals.employments),
      },
      {
        key: "days",
        label: text.requestedDays,
        value: formatLeaveReportDays(report.totals.days, locale),
      },
    ],
    rows: report.statuses.map((row) => ({
      id: row.status,
      actionLabel: text[row.status],
      actionDisabled: row.counts.requests === 0,
      cells: [
        text[row.status],
        number.format(row.counts.requests),
        number.format(row.counts.employments),
        formatLeaveReportDays(row.counts.days, locale),
      ],
    })),
  };
}

export function leaveReportRequestsView(page: LeaveReportRequestPage, locale: Locale) {
  const text = reportingMessages(locale);
  return page.items.map((row) => ({
    id: row.id,
    actionLabel: `${row.employeeName} · ${row.id}`,
    cells: [
      row.employeeName,
      row.employeeNumber,
      row.typeName,
      text[row.status],
      formatLeaveReportDays(row.days, locale),
      formatLeaveReportDays(row.requestDays, locale),
    ],
  }));
}
