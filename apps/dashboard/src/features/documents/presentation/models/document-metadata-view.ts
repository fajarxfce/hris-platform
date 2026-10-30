import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { Document } from "../../domain/entities/document";
import type {
  DocumentBusinessMetadata,
  DocumentMetadataPage,
} from "../../domain/entities/document-metadata";
import { documentMessages } from "../i18n/document-messages";
import { documentMetadataMessages } from "../i18n/document-metadata-messages";

export function documentMetadataProperties(
  value: DocumentBusinessMetadata | null | undefined,
  locale: Locale,
) {
  if (!value) return [];
  const text = documentMetadataMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  const day = (input: string | null) =>
    input === null ? text.empty : date.format(new Date(`${input}T00:00:00Z`));
  return [
    { label: text.category, value: text.categories[value.category] },
    { label: text.identityNumber, value: value.identityNumber ?? text.empty },
    { label: text.issuingCountry, value: value.issuingCountry ?? text.empty },
    { label: text.issuedOn, value: day(value.issuedOn) },
    { label: text.validUntil, value: day(value.validUntil) },
  ];
}
export function documentMetadataRows(page: DocumentMetadataPage, locale: Locale, timezone: string) {
  const text = documentMetadataMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  return page.items.map((row) => ({
    id: String(row.version),
    actionLabel: `${text.version} ${row.version}`,
    cells: [
      String(row.version),
      text.categories[row.metadata.category],
      row.metadata.identityNumber ?? text.empty,
      row.metadata.issuingCountry ?? text.empty,
      row.metadata.issuedOn ?? text.empty,
      row.metadata.validUntil ?? text.empty,
      row.actorId ?? text.existing,
      `${date.format(new Date(row.recordedAt))} (${timezone})`,
      row.reason ?? text.initial,
    ],
  }));
}

export function documentMetadataEditorProperties(document: Document, locale: Locale) {
  const text = documentMetadataMessages(locale),
    documents = documentMessages(locale);
  return [
    { label: documents.title, value: document.title },
    { label: documents.classification, value: documents[document.classification] },
    { label: text.documentVersion, value: String(document.version) },
  ];
}
