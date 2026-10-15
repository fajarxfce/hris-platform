import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollInput } from "../../domain/entities/payroll-input";
import type { PayrollInputReview } from "../../domain/entities/payroll-input-review";
import type { PayrollInputTerms } from "../../domain/entities/payroll-input-terms";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollInputView(input: PayrollInput, locale: Locale, timezone: string) {
  const text = payrollInputMessages(locale),
    t = input.terms;
  return {
    title: `${text.revision} ${input.version}`,
    properties: [
      { label: text.version, value: String(input.version) },
      { label: text.status, value: text[input.status] },
      { label: text.month, value: input.earningsMonth },
      { label: text.nonCashTaxable, value: payrollAmountView(t.nonCashTaxable, locale) },
      { label: text.scheduledMonthUnits, value: t.scheduledMonthUnits ?? text.notSpecified },
      { label: text.reviewReference, value: t.reviewReference },
      { label: text.workVersion, value: String(input.workPeriodVersion) },
      { label: text.employmentVersion, value: String(input.employmentVersion) },
      { label: text.preparedBy, value: input.preparedBy },
      { label: text.verifiedBy, value: input.verifiedBy ?? "—" },
      {
        label: `${text.recorded} (${timezone})`,
        value: new Intl.DateTimeFormat(locale, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: timezone,
        }).format(new Date(input.recordedAt)),
      },
      { label: text.reason, value: input.reason },
    ],
    ...payrollInputTermsView(t, locale),
  };
}
export function payrollInputTermsView(t: PayrollInputTerms, locale: Locale) {
  const text = payrollInputMessages(locale),
    h = t.holidayAllowance;
  return {
    earnings: t.earnings.map((line) => ({
      id: line.code,
      cells: [
        line.code,
        line.name,
        payrollAmountView(line.amount, locale),
        line.taxable ? text.yes : text.no,
      ],
    })),
    deductions: t.deductions.map((line) => ({
      id: line.code,
      cells: [line.code, line.name, payrollAmountView(line.amount, locale)],
    })),
    days: t.dayResolutions.map((day) => ({
      id: `${day.workDate}/${day.portion}`,
      cells: [day.workDate, text[day.portion], text[day.disposition], day.reference],
    })),
    holiday: h
      ? [
          { label: text.holidayKind, value: text[h.kind] },
          { label: text.holidayDate, value: h.holidayDate },
          { label: text.serviceFrom, value: h.continuousServiceFrom },
          { label: text.priorPayment, value: text[h.priorPayment] },
          { label: text.reviewReference, value: h.reviewReference },
          {
            label: text.promisedAmount,
            value:
              h.promisedAmount === null
                ? text.notSpecified
                : payrollAmountView(h.promisedAmount, locale),
          },
        ]
      : null,
  };
}
export function payrollInputReviewView(
  review: PayrollInputReview,
  locale: Locale,
  timezone: string,
) {
  const text = payrollInputMessages(locale),
    s = review.source;
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return {
    source: [
      { label: text.month, value: review.earningsMonth },
      { label: text.status, value: s?.closed ? text.closed : text.notClosed },
      { label: text.workVersion, value: s ? String(s.version) : "—" },
      { label: text.included, value: s?.includesEmployee ? text.yes : text.no },
    ],
    notice: review.frozen
      ? text.frozen
      : !s?.closed
        ? text.noSource
        : !s.includesEmployee
          ? text.employeeMissing
          : review.current &&
              (review.current.employmentVersion !== review.employee.employmentVersion ||
                review.current.workJobId !== s.jobId ||
                review.current.workPeriodVersion !== s.version)
            ? text.sourceChanged
            : null,
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
