import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompensationReview } from "../../domain/entities/compensation-review";
import type { CompensationRevision } from "../../domain/entities/compensation-revision";
import type { EmployeeCompensation } from "../../domain/entities/employee-compensation";
import { compensationMessages } from "../i18n/compensation-messages";
import { payrollAmountView } from "./payroll-policy-view";
export function compensationView(value: EmployeeCompensation, locale: Locale) {
  const text = compensationMessages(locale),
    t = value.terms,
    b = t.payBasis;
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return {
    title: `${text.revision} ${value.appliedRevision}`,
    effectiveFrom: value.effectiveFrom,
    groups: [
      {
        title: text.earnings,
        items: [
          { label: text.basicSalary, value: payrollAmountView(t.basicSalary, locale) },
          { label: text.treatment, value: text[t.treatment] },
        ],
      },
      {
        title: text.tax,
        items: [
          { label: text.residency, value: text[t.tax.residency] },
          { label: text.ptkp, value: text[t.tax.ptkp] },
          { label: text.residenceCountry, value: t.tax.residenceCountry },
          {
            label: text.subjectiveFrom,
            value: t.tax.subjectiveFrom
              ? date.format(new Date(`${t.tax.subjectiveFrom}T00:00:00Z`))
              : "—",
          },
          {
            label: text.subjectiveUntil,
            value: t.tax.subjectiveUntil
              ? date.format(new Date(`${t.tax.subjectiveUntil}T00:00:00Z`))
              : "—",
          },
          { label: text.verifiedOn, value: date.format(new Date(`${t.tax.verifiedOn}T00:00:00Z`)) },
          { label: text.verificationReference, value: t.tax.verificationReference },
        ],
      },
      {
        title: text.insurance,
        items: [
          { label: text.insuranceWage, value: payrollAmountView(t.insuranceWage, locale) },
          {
            label: text.programs,
            value: t.insurancePrograms.map((program) => text[program]).join(", ") || "—",
          },
          { label: text.insuranceExemptionReason, value: t.insuranceExemptionReason ?? "—" },
          { label: text.accidentRisk, value: text[t.accidentRisk] },
          { label: text.additionalHealthDependents, value: String(t.additionalHealthDependents) },
        ],
      },
      {
        title: text.deductions,
        items: [
          {
            label: text.additionalRetirementContribution,
            value: payrollAmountView(t.additionalRetirementContribution, locale),
          },
          { label: text.qualifiedDonation, value: payrollAmountView(t.qualifiedDonation, locale) },
          { label: text.otherNetDeduction, value: payrollAmountView(t.otherNetDeduction, locale) },
        ],
      },
    ],
    earnings: t.fixedEarnings.map((line) => ({
      id: line.code,
      cells: [
        line.code,
        line.name,
        payrollAmountView(line.amount, locale),
        line.taxable ? text.yes : text.no,
      ],
    })),
    basis: b
      ? [
          { label: text.overtimeRule, value: b.overtimeRuleId },
          { label: text.holidayAllowanceRule, value: b.holidayAllowanceRuleId },
          { label: text.proration, value: text[b.proration] },
          { label: text.workWeek, value: text[b.workWeek] },
          { label: text.shortestWorkDay, value: b.shortestWorkDay ? text[b.shortestWorkDay] : "—" },
          { label: text.overtimeEligibility, value: text[b.overtimeEligibility] },
          { label: text.overtimeExemptionReference, value: b.overtimeExemptionReference ?? "—" },
          {
            label: text.regularNonFixedWage,
            value: payrollAmountView(b.regularNonFixedWage, locale),
          },
          { label: text.serviceMonthConvention, value: text[b.serviceMonthConvention] },
          { label: text.rounding, value: text[b.rounding] },
          { label: text.reviewReference, value: b.reviewReference },
        ]
      : null,
  };
}
export function compensationHistoryView(
  review: CompensationReview,
  locale: Locale,
  timezone: string,
) {
  const text = compensationMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    });
  return review.history.items.map((row) => ({
    id: String(row.compensation.appliedRevision),
    actionLabel: `${text.revision} ${row.compensation.appliedRevision}`,
    cells: [
      String(row.compensation.appliedRevision),
      row.compensation.effectiveFrom,
      payrollAmountView(row.compensation.terms.basicSalary, locale),
      time.format(new Date(row.recordedAt)),
      row.reason,
    ],
  }));
}
export function compensationEvidenceView(
  row: CompensationRevision,
  locale: Locale,
  timezone: string,
) {
  const text = compensationMessages(locale);
  return [
    { label: text.actor, value: row.actorId },
    {
      label: `${text.recorded} (${timezone})`,
      value: new Intl.DateTimeFormat(locale, {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: timezone,
      }).format(new Date(row.recordedAt)),
    },
    { label: text.reason, value: row.reason },
  ];
}
