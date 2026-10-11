import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { Announcement } from "../../domain/entities/announcement";
import type { AnnouncementPage } from "../../domain/entities/announcement-page";
import { announcementMessages } from "../i18n/announcement-messages";

export function announcementRows(
  page: AnnouncementPage,
  history: boolean,
  locale: Locale,
  timezone: string,
) {
  const text = announcementMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((item) => ({
    id: history ? String(item.version) : item.id,
    actionLabel: item.title + (history ? ` · ${text.version} ${number.format(item.version)}` : ""),
    cells: [
      item.title,
      text[item.status],
      text[item.audienceKind],
      number.format(item.version),
      date.format(new Date(item.recordedAt)),
    ],
  }));
}
export function announcementView(item: Announcement, locale: Locale, timezone: string) {
  const text = announcementMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  return [
    { label: text.status, value: text[item.status] },
    { label: text.version, value: number.format(item.version) },
    { label: text.audience, value: text[item.audienceKind] },
    { label: text.targets, value: number.format(item.targetCount) },
    {
      label: text.acknowledgement,
      value: item.acknowledgementRequired ? text.required : text.optional,
    },
    { label: text.recipients, value: number.format(item.recipientCount) },
    { label: text.recordedAt, value: date.format(new Date(item.recordedAt)) },
    {
      label: text.scheduledFor,
      value: item.scheduledFor ? date.format(new Date(item.scheduledFor)) : text.absent,
    },
    {
      label: text.publishedAt,
      value: item.publishedAt ? date.format(new Date(item.publishedAt)) : text.absent,
    },
    { label: text.attempts, value: number.format(item.publicationAttempts) },
  ];
}
