import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ExpenseClaimReview } from "../../domain/entities/expense-claim-review";
import type { ExpenseClaimPage } from "../../domain/entities/expense-claim-summary";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import { expenseAmountView } from "./expense-amount-view";
export function expenseClaimRows(page: ExpenseClaimPage, locale: Locale, timezone: string) {
  const text = expenseClaimMessages(locale);
  const time = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: timezone });
  return page.items.map((claim) => ({
    id: claim.id,
    actionLabel: claim.title,
    cells: [
      claim.title,
      `${claim.employeeName} · ${claim.employeeNumber}`,
      text[claim.status],
      expenseAmountView(claim.totalAmount, locale),
      time.format(new Date(claim.createdAt)),
    ],
  }));
}
export function expenseClaimView(
  review: ExpenseClaimReview,
  selectedChange: string | null,
  locale: Locale,
  timezone: string,
) {
  const text = expenseClaimMessages(locale),
    claim = review.claim;
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const change = review.history.items.find((entry) => String(entry.version) === selectedChange);
  return {
    title: claim.draft.title,
    description: claim.draft.description,
    properties: [
      { label: text.employee, value: claim.draft.employeeName },
      { label: text.employeeNumber, value: claim.draft.employeeNumber },
      { label: text.status, value: text[claim.status] },
      { label: text.total, value: expenseAmountView(claim.draft.totalAmount, locale) },
      { label: `${text.created} (${timezone})`, value: time.format(new Date(claim.createdAt)) },
      { label: text.version, value: number.format(claim.version) },
    ],
    lines: claim.draft.lines.map((line) => ({
      id: line.id,
      cells: [
        date.format(new Date(`${line.occurredOn}T00:00:00Z`)),
        line.description,
        expenseAmountView(line.amount, locale),
        number.format(line.receiptRevisionIds.length),
      ],
    })),
    submissions: review.submissions.map((submission) => ({
      id: submission.id,
      actionLabel: `${text.number} ${submission.number}`,
      cells: [
        number.format(submission.number),
        submission.title,
        expenseAmountView(submission.totalAmount, locale),
        time.format(new Date(submission.submittedAt)),
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
    change: change
      ? [
          { label: text.version, value: number.format(change.version) },
          { label: text.action, value: text[change.kind] },
          { label: text.status, value: text[change.status] },
          { label: text.draftRevision, value: number.format(change.draftRevision) },
          {
            label: `${text.recorded} (${timezone})`,
            value: time.format(new Date(change.recordedAt)),
          },
          { label: text.actor, value: change.actorId },
          { label: text.reason, value: change.reason || text.none },
        ]
      : null,
    payments: review.payments.map((payment) => ({
      id: payment.itemId,
      cells: [
        text[payment.status],
        time.format(new Date(payment.createdAt)),
        payment.releasedAt ? time.format(new Date(payment.releasedAt)) : text.none,
        payment.settledAt ? time.format(new Date(payment.settledAt)) : text.none,
      ],
    })),
  };
}
