import type { Locale } from "../../../../core/presentation/i18n/messages";
import { formatIdrAmount } from "../../../../core/presentation/money/format-idr-amount";
import type { PayrollCostReport } from "../../domain/entities/payroll-cost-report";
import { reportingMessages } from "../i18n/reporting-messages";

export function payrollCostReportView(report: PayrollCostReport, locale: Locale) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const month = new Intl.DateTimeFormat(locale, {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
  const keys = [
    "assessments",
    "employments",
    "revisedAssessments",
    "employerCost",
    "taxableGross",
    "employeeDeductions",
    "withheld",
    "takeHome",
  ] as const;
  const amount = (value: number | string) =>
    typeof value === "number" ? number.format(value) : formatIdrAmount(value, locale);
  return {
    metrics: keys.map((key) => ({ key, label: text[key], value: amount(report.totals[key]) })),
    rows: report.months.map((row) => ({
      id: row.month,
      actionLabel: month.format(new Date(`${row.month}-01T00:00:00Z`)),
      actionDisabled: row.counts.assessments === 0,
      cells: [
        month.format(new Date(`${row.month}-01T00:00:00Z`)),
        ...keys.map((key) => amount(row.counts[key])),
      ],
    })),
  };
}
