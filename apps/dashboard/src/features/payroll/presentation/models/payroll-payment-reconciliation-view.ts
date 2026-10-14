import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollPaymentOutcome } from "../../domain/entities/payroll-payment-outcome";
import type { PayrollPaymentReview } from "../../domain/entities/payroll-payment-review";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPaymentReconciliationMessages } from "../i18n/payroll-payment-reconciliation-messages";
import { payrollAmountView } from "./payroll-policy-view";

export function payrollPaymentReconciliationView(
  review: PayrollPaymentReview,
  results: readonly PayrollPaymentOutcome[],
  selectedItemId: string | null,
  locale: Locale,
) {
  const text = payrollPaymentReconciliationMessages(locale),
    common = payrollPaymentMessages(locale),
    number = new Intl.NumberFormat(locale);
  const outcomes = new Map(results.map((result) => [result.itemId, result])),
    pending = review.batch.items.filter((item) => item.status === "PENDING");
  const selected = pending.find((item) => item.id === selectedItemId);
  return {
    title: review.batch.title,
    properties: [
      { label: common.total, value: payrollAmountView(review.batch.totalAmount, locale) },
      { label: text.entered, value: number.format(results.length) },
      { label: text.remaining, value: number.format(pending.length - results.length) },
    ],
    rows: pending.map((item) => {
      const outcome = outcomes.get(item.id);
      return {
        id: item.id,
        actionLabel: `${item.employeeName} · ${item.employeeNumber} · ${item.taxMonth}`,
        cells: [
          `${item.employeeName} · ${item.employeeNumber} · ${item.taxMonth}`,
          item.destination.bankCode,
          `•••• ${item.destination.accountNumber.slice(-4)}`,
          payrollAmountView(item.amount, locale),
          outcome ? common[outcome.status] : text.notEntered,
        ],
      };
    }),
    selected: selected
      ? {
          id: selected.id,
          original: outcomes.get(selected.id) ?? null,
          properties: [
            {
              label: common.employee,
              value: `${selected.employeeName} · ${selected.employeeNumber}`,
            },
            { label: common.amount, value: payrollAmountView(selected.amount, locale) },
            { label: common.taxMonth, value: selected.taxMonth },
            { label: common.plannedDate, value: selected.plannedPaymentDate },
            { label: common.assessmentId, value: selected.assessmentId },
            { label: common.bank, value: selected.destination.bankCode },
            { label: common.accountName, value: selected.destination.accountName },
            { label: common.accountNumber, value: selected.destination.accountNumber },
          ],
        }
      : null,
  };
}
