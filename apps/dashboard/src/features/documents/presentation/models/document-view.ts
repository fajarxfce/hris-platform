import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { Document } from "../../domain/entities/document";
import type { DocumentEmployeeDirectory } from "../../domain/entities/document-employee";
import type { DocumentFolder } from "../../domain/entities/document-folder";
import type { DocumentRevisionPage } from "../../domain/entities/document-revision";
import type { DocumentRevisionReview } from "../../domain/entities/document-revision-review";
import { documentMessages } from "../i18n/document-messages";

export function documentEmployeeRows(directory: DocumentEmployeeDirectory, locale: Locale) {
  const text = documentMessages(locale);
  return directory.employees.items.map((employee) => ({
    id: employee.id,
    actionLabel: `${employee.name} (${employee.employeeNumber})`,
    cells: [employee.employeeNumber, employee.name, text[employee.status]],
  }));
}
export function documentRows(folder: DocumentFolder, locale: Locale, timezone: string) {
  const text = documentMessages(locale),
    date = new Intl.DateTimeFormat(locale, { timeZone: timezone, dateStyle: "medium" });
  return folder.documents.items.map((document) => ({
    id: document.id,
    actionLabel: document.title,
    cells: [
      document.title,
      text[document.classification],
      String(document.revisionCount),
      date.format(new Date(document.createdAt)),
    ],
  }));
}
export function documentProperties(document: Document, locale: Locale, timezone: string) {
  const text = documentMessages(locale),
    date = new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
  return [
    { label: text.documentId, value: document.id },
    { label: text.classification, value: text[document.classification] },
    { label: text.created, value: `${date.format(new Date(document.createdAt))} (${timezone})` },
    { label: text.revisions, value: String(document.revisionCount) },
    { label: text.documentVersion, value: String(document.version) },
  ];
}
export function documentRevisionRows(page: DocumentRevisionPage, locale: Locale, timezone: string) {
  const text = documentMessages(locale),
    date = new Intl.DateTimeFormat(locale, { timeZone: timezone, dateStyle: "medium" });
  return page.items.map((revision) => ({
    id: revision.id,
    actionLabel: `${revision.number}: ${revision.fileName}`,
    cells: [
      String(revision.number),
      revision.fileName,
      text[revision.status],
      documentSize(revision.size, locale),
      date.format(new Date(revision.createdAt)),
    ],
  }));
}
export function documentRevisionView(
  review: DocumentRevisionReview,
  locale: Locale,
  timezone: string,
) {
  const text = documentMessages(locale),
    revision = review.revision;
  const date = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  const instant = (value: string): string => `${date.format(new Date(value))} (${timezone})`;
  return {
    title: `${text.revision} ${revision.number}`,
    properties: [
      { label: text.revisionId, value: revision.id },
      { label: text.fileName, value: revision.fileName },
      { label: text.status, value: text[revision.status] },
      { label: text.mediaType, value: revision.mediaType },
      { label: text.size, value: documentSize(revision.size, locale) },
      { label: text.uploaded, value: documentSize(revision.uploadedBytes, locale) },
      { label: text.created, value: instant(revision.createdAt) },
      ...(revision.status === "UPLOADING"
        ? [{ label: text.expires, value: instant(revision.expiresAt) }]
        : []),
      ...(revision.validatedAt
        ? [{ label: text.validated, value: instant(revision.validatedAt) }]
        : []),
      { label: text.reason, value: revision.reason },
      { label: text.digest, value: revision.sha256 },
      ...(revision.failureCode
        ? [
            {
              label: text.validationFailure,
              value: failureMessage(
                { code: revision.failureCode, fields: {}, parameters: {} },
                locale,
              ),
            },
          ]
        : []),
    ],
    attempts: review.attempts.map((attempt) => ({
      id: attempt.jobId,
      cells: [String(attempt.number), instant(attempt.createdAt), attempt.reason],
    })),
  };
}
export function documentSize(bytes: number, locale: Locale): string {
  const divisor = bytes >= 1024 * 1024 ? 1024 * 1024 : bytes >= 1024 ? 1024 : 1;
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(bytes / divisor)} ${divisor === 1 ? "B" : divisor === 1024 ? "KiB" : "MiB"}`;
}
