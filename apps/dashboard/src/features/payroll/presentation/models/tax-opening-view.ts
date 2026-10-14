import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { TaxOpening } from "../../domain/entities/tax-opening";
import type { TaxOpeningReview } from "../../domain/entities/tax-opening-review";
import { taxOpeningMessages } from "../i18n/tax-opening-messages";
import { payrollAmountView } from "./payroll-policy-view";
export function taxOpeningView(opening: TaxOpening, locale: Locale, timezone: string) {
  const text = taxOpeningMessages(locale),
    t = opening.terms,
    h = t.history;
  return {
    title: `${text.revision} ${opening.version}`,
    properties: [
      { label: text.version, value: String(opening.version) },
      { label: text.status, value: text[opening.status] },
      { label: text.year, value: opening.year },
      { label: text.throughMonth, value: text.months[t.throughMonth] ?? "—" },
      { label: text.residency, value: text[t.residency] },
      { label: text.ptkp, value: t.ptkp },
      { label: text.reference, value: t.reference },
      { label: text.preparedBy, value: opening.preparedBy },
      { label: text.verifiedBy, value: opening.verifiedBy ?? "—" },
      {
        label: `${text.recorded} (${timezone})`,
        value: new Intl.DateTimeFormat(locale, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: timezone,
        }).format(new Date(opening.recordedAt)),
      },
      { label: text.reason, value: opening.reason },
    ],
    history: [
      { label: text.taxableGross, value: payrollAmountView(h.taxableGross, locale) },
      {
        label: text.retirementContributions,
        value: payrollAmountView(h.retirementContributions, locale),
      },
      { label: text.qualifiedDonations, value: payrollAmountView(h.qualifiedDonations, locale) },
      { label: text.withheld, value: payrollAmountView(h.withheld, locale) },
      { label: text.employmentMonths, value: String(h.employmentMonths) },
      { label: text.previousEmployerNet, value: payrollAmountView(h.previousEmployerNet, locale) },
      {
        label: text.previousEmployerWithheld,
        value: payrollAmountView(h.previousEmployerWithheld, locale),
      },
    ],
  };
}
export function taxOpeningReviewView(review: TaxOpeningReview, locale: Locale, timezone: string) {
  const text = taxOpeningMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    });
  return {
    notice: review.frozen ? text.frozen : null,
    history: review.history.items.map((row) => ({
      id: String(row.version),
      actionLabel: `${text.revision} ${row.version}`,
      cells: [
        String(row.version),
        text[row.status],
        time.format(new Date(row.recordedAt)),
        row.reason,
      ],
    })),
  };
}
