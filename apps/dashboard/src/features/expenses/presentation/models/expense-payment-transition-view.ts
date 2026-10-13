import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
import { expensePaymentSnapshot } from "../../domain/entities/expense-payment-snapshot";
import { canTransitionExpensePayment } from "../../domain/policies/expense-payment-transition-policy";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import { expensePaymentTransitionMessages } from "../i18n/expense-payment-transition-messages";
import { expenseAmountView } from "./expense-amount-view";
import {
  expensePaymentTransitionForIntent,
  expensePaymentTransitionIntents,
} from "./expense-payment-transition-intent";
import { expensePaymentView } from "./expense-payment-view";
export function expensePaymentTransitionActions(
  access: CompanyAccess,
  review: ExpensePaymentReview,
  locale: Locale,
) {
  const text = expensePaymentTransitionMessages(locale),
    snapshot = expensePaymentSnapshot(review);
  return expensePaymentTransitionIntents
    .filter((intent) =>
      canTransitionExpensePayment(access, snapshot, expensePaymentTransitionForIntent[intent]),
    )
    .map((intent) => ({
      intent,
      label: intent === "release" ? text.releaseAction : text.cancelAction,
    }));
}
export function expensePaymentTransitionView(
  review: ExpensePaymentReview,
  selectedItem: string | null,
  locale: Locale,
  timezone: string,
) {
  const text = expensePaymentMessages(locale),
    detail = expensePaymentView(review, selectedItem, null, locale, timezone);
  return {
    title: review.batch.title,
    properties: [
      { label: text.status, value: text[review.batch.status] },
      { label: text.total, value: expenseAmountView(review.batch.totalAmount, locale) },
      { label: text.items, value: new Intl.NumberFormat(locale).format(review.batch.itemCount) },
    ],
    item: detail.item,
    instructions: review.batch.items.map((item) => ({
      id: item.id,
      actionLabel: `${item.employeeName} · ${item.employeeNumber}`,
      cells: [
        `${item.employeeName} · ${item.employeeNumber}`,
        item.destination.accountName,
        item.destination.bankCode,
        item.destination.accountNumber,
        expenseAmountView(item.amount, locale),
      ],
    })),
  };
}
