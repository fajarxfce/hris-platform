import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AudienceGroup, AudienceGroupPage } from "../../domain/entities/audience-group";
import { audienceGroupMessages } from "../i18n/audience-group-messages";

export const audienceGroupsPath = "/communications/audience-groups";
export function audienceGroupRows(page: AudienceGroupPage, locale: Locale, timezone: string) {
  const text = audienceGroupMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((group) => ({
    id: group.id,
    actionLabel: group.name,
    cells: [
      group.name,
      group.active ? text.active : text.inactive,
      number.format(group.memberCount),
      number.format(group.version),
      date.format(new Date(group.recordedAt)),
    ],
  }));
}
export function audienceGroupView(group: AudienceGroup, locale: Locale, timezone: string) {
  const text = audienceGroupMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  return [
    { label: text.status, value: group.active ? text.active : text.inactive },
    { label: text.members, value: number.format(group.memberCount) },
    { label: text.version, value: number.format(group.version) },
    { label: text.recorded, value: date.format(new Date(group.recordedAt)) },
    { label: text.reason, value: group.reason },
  ];
}
