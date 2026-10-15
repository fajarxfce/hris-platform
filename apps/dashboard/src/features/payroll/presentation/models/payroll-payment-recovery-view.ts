import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollPaymentRecoveryReview } from "../../domain/entities/payroll-payment-recovery-review";
import { payrollPaymentRecoveryMessages } from "../i18n/payroll-payment-recovery-messages";
import { payrollPayslipSettlementView } from "./payroll-payslip-view";
import { payrollAmountView } from "./payroll-policy-view";

export function payrollPaymentRecoveryView(
  review: PayrollPaymentRecoveryReview,
  selected: string | null,
  locale: Locale,
  timezone: string,
) {
  const text = payrollPaymentRecoveryMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "medium",
      timeZone: timezone,
    }),
    record = review.recoveries.find((entry) => entry.id === selected);
  return {
    employee: `${review.employeeName} · ${review.employeeNumber}`,
    properties: [
      { label: text.employee, value: review.employeeName },
      { label: text.employeeNumber, value: review.employeeNumber },
      { label: text.month, value: review.taxMonth },
      {
        label: `${text.published} (${timezone})`,
        value: time.format(new Date(review.publishedAt)),
      },
      { label: text.paymentVersion, value: String(review.payments.version) },
      { label: `${text.earliest} (UTC)`, value: review.earliestConfirmationAt },
    ],
    settlement: payrollPayslipSettlementView(review.payments, locale) ?? [],
    blockers: review.blockers.map((blocker) => ({ id: blocker, text: text[blocker] })),
    rows: review.recoveries.map((entry) => ({
      id: entry.id,
      actionLabel: entry.transactionReference,
      cells: [
        entry.transactionReference,
        payrollAmountView(entry.amount, locale),
        String(entry.assessmentRevision),
        time.format(new Date(entry.occurredAt)),
        time.format(new Date(entry.recordedAt)),
      ],
    })),
    selected: record
      ? [
          { label: text.returnId, value: record.id },
          { label: text.amount, value: payrollAmountView(record.amount, locale) },
          { label: text.reference, value: record.transactionReference },
          { label: text.revision, value: String(record.assessmentRevision) },
          { label: text.paymentVersion, value: String(record.paymentVersion) },
          {
            label: `${text.occurred} (${timezone})`,
            value: time.format(new Date(record.occurredAt)),
          },
          {
            label: `${text.recorded} (${timezone})`,
            value: time.format(new Date(record.recordedAt)),
          },
          { label: text.actor, value: record.actorId },
          { label: text.reason, value: record.reason },
        ]
      : null,
  };
}
