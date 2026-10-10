import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PersonProfile, PersonProfileFields } from "../../domain/entities/person-profile";
import type {
  PersonProfileHistoryPage,
  PersonProfileRevision,
} from "../../domain/entities/person-profile-revision";
import { personProfileMessages } from "../i18n/person-profile-messages";

export function personProfileFieldsView(profile: PersonProfileFields, locale: Locale) {
  const text = personProfileMessages(locale);
  const country =
    new Intl.DisplayNames([locale], { type: "region" }).of(profile.nationality) ??
    profile.nationality;
  return [
    { label: text.legalName, value: profile.legalName },
    {
      label: text.birthDate,
      value:
        profile.birthDate === null
          ? text.none
          : new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }).format(
              new Date(`${profile.birthDate}T00:00:00Z`),
            ),
    },
    { label: text.nationality, value: `${country} (${profile.nationality})` },
    { label: text.email, value: profile.email ?? text.none },
  ];
}
export function personProfileView(
  profile: PersonProfile,
  ownerName: string | null,
  locale: Locale,
) {
  const text = personProfileMessages(locale);
  return [
    ...personProfileFieldsView(profile, locale),
    { label: text.owner, value: ownerName ?? profile.ownerCompanyId },
    { label: text.version, value: new Intl.NumberFormat(locale).format(profile.version) },
    { label: text.personId, value: profile.personId },
    { label: text.accountId, value: profile.accountId ?? text.none },
  ];
}
export function personProfileHistoryView(page: PersonProfileHistoryPage, locale: Locale) {
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: "UTC",
  });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((item) => ({
    id: String(item.revision),
    cells: [number.format(item.revision), item.legalName, date.format(new Date(item.recordedAt))],
  }));
}
export function personProfileRevisionView(revision: PersonProfileRevision, locale: Locale) {
  const text = personProfileMessages(locale);
  return [
    { label: text.revision, value: new Intl.NumberFormat(locale).format(revision.revision) },
    ...personProfileFieldsView(revision, locale),
    { label: text.accountId, value: revision.accountId ?? text.none },
    { label: text.actor, value: revision.actorId ?? text.none },
    { label: text.reason, value: revision.reason },
    {
      label: text.recorded,
      value: new Intl.DateTimeFormat(locale, {
        dateStyle: "medium",
        timeStyle: "medium",
        timeZone: "UTC",
      }).format(new Date(revision.recordedAt)),
    },
  ];
}
