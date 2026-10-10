import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LifecycleEvent, LifecycleHistoryPage } from "../../domain/entities/lifecycle-event";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";

export function lifecycleHistoryView(page: LifecycleHistoryPage, locale: Locale, timezone: string) {
  const text = lifecycleCaseMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((event) => ({
    id: String(event.version),
    cells: [
      number.format(event.version),
      text[event.action],
      event.taskKey ?? "—",
      date.format(new Date(event.recordedAt)),
    ],
  }));
}
export function lifecycleEventView(event: LifecycleEvent, locale: Locale, timezone: string) {
  const text = lifecycleCaseMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return [
    { label: text.version, value: new Intl.NumberFormat(locale).format(event.version) },
    { label: text.action, value: text[event.action] },
    { label: text.key, value: event.taskKey ?? "—" },
    { label: text.reason, value: event.reason },
    { label: `${text.recordedAt} (${timezone})`, value: date.format(new Date(event.recordedAt)) },
    { label: text.actor, value: event.actorId },
    { label: text.assignee, value: event.assigneeId ?? text.none },
  ];
}
