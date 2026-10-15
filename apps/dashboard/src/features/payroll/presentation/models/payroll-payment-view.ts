import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollPaymentReview } from "../../domain/entities/payroll-payment-review";
import type { PayrollPaymentPage } from "../../domain/entities/payroll-payment-summary";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollPaymentRows(page: PayrollPaymentPage, locale: Locale, timezone: string) {
  const text = payrollPaymentMessages(locale),
    number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: timezone });
  return page.items.map((batch) => ({
    id: batch.id,
    actionLabel: batch.title,
    cells: [
      batch.title,
      text[batch.status],
      payrollAmountView(batch.totalAmount, locale),
      number.format(batch.itemCount),
      number.format(batch.pendingCount),
      date.format(new Date(batch.createdAt)),
    ],
  }));
}
export function payrollPaymentView(
  review: PayrollPaymentReview,
  selectedItem: string | null,
  selectedChange: string | null,
  locale: Locale,
  timezone: string,
) {
  const text = payrollPaymentMessages(locale),
    number = new Intl.NumberFormat(locale),
    batch = review.batch;
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const item = batch.items.find((entry) => entry.id === selectedItem);
  const change = review.history.items.find((entry) => String(entry.version) === selectedChange);
  const reconciliation = review.results.find((entry) =>
    entry.results.some((result) => result.itemId === item?.id),
  );
  const outcome = reconciliation?.results.find((result) => result.itemId === item?.id);
  return {
    title: batch.title,
    note: {
      PREPARED: text.preparedNote,
      RELEASED: text.releasedNote,
      CLOSED: text.closedNote,
      CANCELLED: text.cancelledNote,
    }[batch.status],
    properties: [
      { label: text.status, value: text[batch.status] },
      { label: text.total, value: payrollAmountView(batch.totalAmount, locale) },
      { label: `${text.created} (${timezone})`, value: time.format(new Date(batch.createdAt)) },
      { label: text.createdBy, value: batch.createdBy },
      {
        label: `${text.released} (${timezone})`,
        value: batch.releasedAt ? time.format(new Date(batch.releasedAt)) : text.none,
      },
      { label: text.releasedBy, value: batch.releasedBy ?? text.none },
      { label: text.version, value: number.format(batch.version) },
      { label: text.batchId, value: batch.id },
    ],
    metrics: [
      { label: text.items, value: number.format(batch.itemCount) },
      {
        label: text.pending,
        value: number.format(batch.items.filter((entry) => entry.status === "PENDING").length),
      },
      {
        label: text.succeeded,
        value: number.format(batch.items.filter((entry) => entry.status === "SUCCEEDED").length),
      },
      {
        label: text.failed,
        value: number.format(batch.items.filter((entry) => entry.status === "FAILED").length),
      },
    ],
    instructions: batch.items.map((entry) => ({
      id: entry.id,
      actionLabel: `${entry.employeeName} · ${entry.employeeNumber} · ${entry.taxMonth}`,
      cells: [
        `${entry.employeeName} · ${entry.employeeNumber} · ${entry.taxMonth}`,
        entry.destination.bankCode,
        `•••• ${entry.destination.accountNumber.slice(-4)}`,
        payrollAmountView(entry.amount, locale),
        text[entry.status],
      ],
    })),
    results: batch.items
      .filter((entry) => entry.occurredAt !== null)
      .map((entry) => ({
        id: entry.id,
        actionLabel: `${entry.employeeName} · ${entry.employeeNumber} · ${entry.taxMonth}`,
        cells: [
          `${entry.employeeName} · ${entry.employeeNumber} · ${entry.taxMonth}`,
          payrollAmountView(entry.amount, locale),
          text[entry.status],
          entry.transactionReference ?? text.none,
          entry.occurredAt ? time.format(new Date(entry.occurredAt)) : text.none,
        ],
      })),
    history: review.history.items.map((entry) => ({
      id: String(entry.version),
      actionLabel: `${text.version} ${entry.version}`,
      cells: [
        number.format(entry.version),
        text[entry.kind],
        text[entry.status],
        time.format(new Date(entry.recordedAt)),
      ],
    })),
    item: item
      ? [
          { label: text.employee, value: item.employeeName },
          { label: text.employeeNumber, value: item.employeeNumber },
          { label: text.amount, value: payrollAmountView(item.amount, locale) },
          { label: text.status, value: text[item.status] },
          { label: text.bank, value: item.destination.bankCode },
          { label: text.accountNumber, value: item.destination.accountNumber },
          { label: text.accountName, value: item.destination.accountName },
          { label: text.paymentReference, value: item.id },
          { label: text.taxMonth, value: item.taxMonth },
          { label: text.revision, value: String(item.assessmentRevision) },
          { label: text.plannedDate, value: item.plannedPaymentDate },
          { label: text.assessmentId, value: item.assessmentId },
          ...(outcome && reconciliation
            ? [
                { label: text.reference, value: outcome.transactionReference ?? text.none },
                {
                  label: `${text.occurred} (${timezone})`,
                  value: time.format(new Date(outcome.occurredAt)),
                },
                { label: text.reason, value: outcome.reason },
                { label: text.actor, value: reconciliation.actorId },
                {
                  label: `${text.recorded} (${timezone})`,
                  value: time.format(new Date(reconciliation.recordedAt)),
                },
              ]
            : []),
        ]
      : null,
    change: change
      ? [
          { label: text.version, value: number.format(change.version) },
          { label: text.action, value: text[change.kind] },
          { label: text.status, value: text[change.status] },
          {
            label: `${text.recorded} (${timezone})`,
            value: time.format(new Date(change.recordedAt)),
          },
          { label: text.actor, value: change.actorId },
          { label: text.reason, value: change.reason },
        ]
      : null,
  };
}
