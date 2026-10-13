import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ReviewedLeaveSelection } from "../../domain/entities/leave-request-preview";
import { leaveMessages } from "../i18n/leave-messages";

export function leaveCreationReviewView(review: ReviewedLeaveSelection, locale: Locale) {
  const text = leaveMessages(locale),
    number = new Intl.NumberFormat(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }),
    preview = review.preview;
  return {
    charged: number.format(Number(preview.chargedDays)),
    timezone: preview.timezone,
    days: preview.days.map((day) => ({
      id: day.workDate,
      cells: [
        date.format(new Date(`${day.workDate}T00:00:00Z`)),
        text[day.portion],
        number.format(Number(day.chargedDays)),
      ],
    })),
    balances: preview.balances.map((balance) => ({
      id: String(balance.year),
      cells: [
        String(balance.year),
        number.format(Number(balance.availableDays)),
        number.format(Number(balance.requestedDays)),
        number.format(Number(balance.remainingDays)),
      ],
    })),
    excluded: preview.excludedDates
      .map((value) => date.format(new Date(`${value}T00:00:00Z`)))
      .join(", "),
  };
}
