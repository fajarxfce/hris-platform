import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  EmploymentHistoryPage,
  EmploymentRevision,
} from "../../domain/entities/employment-revision";
import { peopleMessages } from "../i18n/people-messages";
import { employmentTermsItems } from "./employee-view";

export function employmentHistoryView(page: EmploymentHistoryPage, locale: Locale) {
  const text = peopleMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((item) => ({
    id: String(item.revision),
    cells: [
      number.format(item.revision),
      date.format(new Date(`${item.terms.effectiveFrom}T00:00:00Z`)),
      text[item.terms.status],
      item.cancellation ? text.cancelled : text.retained,
    ],
  }));
}

export function employmentRevisionView(revision: EmploymentRevision, locale: Locale) {
  const text = peopleMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: "UTC",
  });
  return [
    { label: text.revision, value: new Intl.NumberFormat(locale).format(revision.revision) },
    ...employmentTermsItems(revision.terms, locale),
    { label: text.reason, value: revision.reason },
    { label: text.recorded, value: date.format(new Date(revision.recordedAt)) },
    { label: text.disposition, value: revision.cancellation ? text.cancelled : text.retained },
    ...(revision.cancellation === null
      ? []
      : [
          { label: text.cancellationReason, value: revision.cancellation.reason },
          {
            label: text.cancelledAt,
            value: date.format(new Date(revision.cancellation.recordedAt)),
          },
        ]),
  ];
}
