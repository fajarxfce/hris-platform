import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentReview } from "../../domain/entities/payroll-payment-review";
import { payrollPaymentSnapshot } from "../../domain/entities/payroll-payment-snapshot";
import { canTransitionPayrollPayment } from "../../domain/policies/payroll-payment-transition-policy";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPaymentTransitionMessages } from "../i18n/payroll-payment-transition-messages";
import {
  payrollPaymentTransitionForIntent,
  payrollPaymentTransitionIntents,
} from "./payroll-payment-transition-intent";
import { payrollPaymentView } from "./payroll-payment-view";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollPaymentTransitionActions(
  access: CompanyAccess,
  review: PayrollPaymentReview,
  locale: Locale,
) {
  const text = payrollPaymentTransitionMessages(locale),
    snapshot = payrollPaymentSnapshot(review);
  return payrollPaymentTransitionIntents
    .filter((intent) =>
      canTransitionPayrollPayment(access, snapshot, payrollPaymentTransitionForIntent[intent]),
    )
    .map((intent) => ({
      intent,
      label: intent === "release" ? text.releaseAction : text.cancelAction,
    }));
}
export function payrollPaymentTransitionView(
  review: PayrollPaymentReview,
  selectedItem: string | null,
  locale: Locale,
  timezone: string,
) {
  const text = payrollPaymentMessages(locale),
    detail = payrollPaymentView(review, selectedItem, null, locale, timezone);
  return {
    title: review.batch.title,
    properties: [
      { label: text.status, value: text[review.batch.status] },
      { label: text.total, value: payrollAmountView(review.batch.totalAmount, locale) },
      { label: text.items, value: new Intl.NumberFormat(locale).format(review.batch.itemCount) },
    ],
    item: detail.item,
    instructions: review.batch.items.map((item) => ({
      id: item.id,
      actionLabel: `${item.employeeName} · ${item.employeeNumber} · ${item.taxMonth}`,
      cells: [
        `${item.employeeName} · ${item.employeeNumber} · ${item.taxMonth}`,
        item.destination.accountName,
        item.destination.bankCode,
        item.destination.accountNumber,
        payrollAmountView(item.amount, locale),
      ],
    })),
  };
}
