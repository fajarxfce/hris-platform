import type { Locale } from "../../../../core/presentation/i18n/messages";
import { formatIdrAmount } from "../../../../core/presentation/money/format-idr-amount";
import type { ExpenseReport } from "../../domain/entities/expense-report";
import type { ExpenseReportClaimPage } from "../../domain/entities/expense-report-claim";
import { reportingMessages } from "../i18n/reporting-messages";

export function expenseReportView(report: ExpenseReport, locale: Locale) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return {
    metrics: [
      { key: "claims", label: text.expenseClaims, value: number.format(report.totals.claims) },
      {
        key: "employments",
        label: text.employments,
        value: number.format(report.totals.employments),
      },
      { key: "lines", label: text.expenseLines, value: number.format(report.totals.lines) },
      {
        key: "amount",
        label: text.periodAmount,
        value: formatIdrAmount(report.totals.amount, locale),
      },
    ],
    rows: report.statuses.map((row) => ({
      id: row.status,
      actionLabel: text[row.status],
      actionDisabled: row.counts.claims === 0,
      cells: [
        text[row.status],
        number.format(row.counts.claims),
        number.format(row.counts.employments),
        number.format(row.counts.lines),
        formatIdrAmount(row.counts.amount, locale),
      ],
    })),
  };
}

export function expenseReportClaimsView(page: ExpenseReportClaimPage, locale: Locale) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return page.items.map((row) => ({
    id: row.id,
    actionLabel: `${row.employeeName} · ${row.title} · ${row.submissionNumber}`,
    cells: [
      row.employeeName,
      row.employeeNumber,
      row.title,
      number.format(row.submissionNumber),
      text[row.status],
      number.format(row.lines),
      formatIdrAmount(row.amount, locale),
      formatIdrAmount(row.submissionAmount, locale),
    ],
  }));
}
