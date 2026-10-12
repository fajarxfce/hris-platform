import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentRetirementReview } from "../../domain/entities/document-retirement";
import { documentMessages } from "../i18n/document-messages";
import { documentRetirementMessages } from "../i18n/document-retirement-messages";
import { documentRetentionProperties } from "./document-retention-lifecycle-view";

export function documentRetirementView(
  review: DocumentRetirementReview,
  locale: Locale,
  timezone: string,
) {
  const text = documentRetirementMessages(locale),
    documents = documentMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  return {
    properties: [
      { label: text.reference, value: review.revisionId },
      { label: text.number, value: String(review.revisionNumber) },
      { label: text.version, value: String(review.revisionVersion) },
      { label: documents.status, value: documents[review.status] },
      { label: text.published, value: review.published ? text.yes : text.no },
      {
        label: text.reviewedAt,
        value: `${date.format(new Date(review.reviewedAt))} (${timezone})`,
      },
    ],
    retention: documentRetentionProperties(review.retention, locale, timezone),
    blocker:
      review.blockingReason === null
        ? null
        : failureMessage({ code: review.blockingReason, fields: {}, parameters: {} }, locale),
  };
}
