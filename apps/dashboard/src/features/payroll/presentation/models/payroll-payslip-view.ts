import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollPaymentProgress } from "../../domain/entities/payroll-payment-progress";
import type { PayrollPayslip, PayrollPayslipSummary } from "../../domain/entities/payroll-payslip";
import { payrollPayslipMessages } from "../i18n/payroll-payslip-messages";
import { payrollCalculationOutputView } from "./payroll-calculation-output-view";
import { payrollAmountView } from "./payroll-policy-view";

export function payrollPayslipRows(items: readonly PayrollPayslipSummary[], locale: Locale) {
  const month = new Intl.DateTimeFormat(locale, {
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
  return items.map((item) => ({
    id: item.id,
    actionLabel: `${item.employeeName} · ${item.taxMonth}`,
    cells: [
      item.employeeName,
      item.employeeNumber,
      month.format(new Date(`${item.taxMonth}-01T00:00:00Z`)),
      payrollAmountView(item.taxableGross, locale),
      payrollAmountView(item.withheld, locale),
      payrollAmountView(item.takeHome, locale),
    ],
  }));
}
export function payrollPayslipView(payslip: PayrollPayslip, locale: Locale, timezone: string) {
  const text = payrollPayslipMessages(locale),
    summary = payslip.summary;
  const month = new Intl.DateTimeFormat(locale, {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${summary.taxMonth}-01T00:00:00Z`));
  return {
    title: `${summary.employeeName} · ${month}`,
    overview: [
      { label: text.company, value: `${payslip.companyName} (${payslip.companyCode})` },
      { label: text.employee, value: summary.employeeName },
      { label: text.number, value: summary.employeeNumber },
      { label: text.month, value: month },
      { label: text.planned, value: summary.plannedPaymentDate },
      {
        label: `${text.published} (${timezone})`,
        value: new Intl.DateTimeFormat(locale, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: timezone,
        }).format(new Date(summary.publishedAt)),
      },
    ],
    metrics: [
      { label: text.gross, value: payrollAmountView(summary.taxableGross, locale) },
      { label: text.withheld, value: payrollAmountView(summary.withheld, locale) },
      { label: text.takeHome, value: payrollAmountView(summary.takeHome, locale) },
    ],
    output: payrollCalculationOutputView(payslip.calculation, locale),
  };
}
export function payrollPayslipPaymentRows(
  progress: PayrollPaymentProgress | null,
  locale: Locale,
  timezone: string,
) {
  const text = payrollPayslipMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    });
  return (
    progress?.attempts.map((attempt, index) => ({
      id: attempt.itemId,
      cells: [
        String(progress.attempts.length - index),
        text.paymentStatuses[attempt.status],
        time.format(new Date(attempt.createdAt)),
        attempt.releasedAt === null ? "—" : time.format(new Date(attempt.releasedAt)),
        attempt.resolvedAt === null ? "—" : time.format(new Date(attempt.resolvedAt)),
      ],
    })) ?? []
  );
}
