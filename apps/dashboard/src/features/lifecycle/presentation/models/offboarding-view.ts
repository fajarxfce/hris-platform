import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OffboardingReview } from "../../domain/entities/offboarding-review";
import { offboardingMessages } from "../i18n/offboarding-messages";
import { lifecycleCaseView } from "./lifecycle-case-view";

export function offboardingView(review: OffboardingReview, locale: Locale, timezone: string) {
  const text = offboardingMessages(locale);
  const view = lifecycleCaseView(review.case, locale, timezone);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return {
    ...view,
    employment: [
      {
        label: text.lastWorkingDate,
        value: date.format(new Date(`${review.case.targetDate}T00:00:00Z`)),
      },
      { label: text.companyDate, value: date.format(new Date(`${review.today}T00:00:00Z`)) },
      {
        label: text.employmentVersion,
        value: new Intl.NumberFormat(locale).format(review.employmentVersion),
      },
    ],
  };
}
