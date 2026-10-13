import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ExpensePaymentOutcome } from "../../domain/entities/expense-payment-outcome";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import { expenseReconciliationMessages } from "../i18n/expense-reconciliation-messages";
import { expenseAmountView } from "./expense-amount-view";

export function expenseReconciliationView(
  review: ExpensePaymentReview,
  results: readonly ExpensePaymentOutcome[],
  selectedItemId: string | null,
  locale: Locale,
) {
  const text = expenseReconciliationMessages(locale),
    common = expensePaymentMessages(locale),
    number = new Intl.NumberFormat(locale);
  const outcomes = new Map(results.map((result) => [result.itemId, result])),
    pending = review.batch.items.filter((item) => item.status === "PENDING");
  const selected = pending.find((item) => item.id === selectedItemId);
  return {
    title: review.batch.title,
    properties: [
      { label: common.total, value: expenseAmountView(review.batch.totalAmount, locale) },
      { label: text.entered, value: number.format(results.length) },
      { label: text.remaining, value: number.format(pending.length - results.length) },
    ],
    rows: pending.map((item) => {
      const outcome = outcomes.get(item.id);
      return {
        id: item.id,
        actionLabel: `${item.employeeName} · ${item.employeeNumber}`,
        cells: [
          `${item.employeeName} · ${item.employeeNumber}`,
          item.destination.bankCode,
          `•••• ${item.destination.accountNumber.slice(-4)}`,
          expenseAmountView(item.amount, locale),
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
            { label: common.amount, value: expenseAmountView(selected.amount, locale) },
            { label: common.bank, value: selected.destination.bankCode },
            { label: common.accountName, value: selected.destination.accountName },
            { label: common.accountNumber, value: selected.destination.accountNumber },
          ],
        }
      : null,
  };
}
