import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AnnouncementAudiencePreview } from "../../domain/entities/announcement-audience-preview";
import type { AnnouncementReview } from "../../domain/entities/announcement-review";
import { announcementPublicationMessages } from "../i18n/announcement-publication-messages";

export function announcementPreviewView(
  preview: AnnouncementAudiencePreview,
  locale: Locale,
  timezone: string,
) {
  const text = announcementPublicationMessages(locale);
  return [
    { label: text.count, value: new Intl.NumberFormat(locale).format(preview.recipientCount) },
    {
      label: text.asOf,
      value: new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }).format(
        new Date(`${preview.asOfDate}T00:00:00Z`),
      ),
    },
    {
      label: text.evaluated,
      value: new Intl.DateTimeFormat(locale, {
        dateStyle: "medium",
        timeStyle: "medium",
        timeZone: timezone,
      }).format(new Date(preview.evaluatedAt)),
    },
  ];
}
export function announcementJobView(review: AnnouncementReview, locale: Locale) {
  const text = announcementPublicationMessages(locale);
  return review.publicationJob
    ? [
        { label: text.jobStatus, value: text[review.publicationJob.status] },
        {
          label: text.cancellation,
          value: review.publicationJob.cancellationRequested ? text.yes : text.no,
        },
      ]
    : [];
}
