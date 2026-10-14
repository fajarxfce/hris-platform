import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollPolicy } from "../../domain/entities/payroll-policy";
import type { PayrollPolicyReview } from "../../domain/entities/payroll-policy-review";
import type { PayrollPolicyRevision } from "../../domain/entities/payroll-policy-revision";
import { payrollPolicyMessages } from "../i18n/payroll-policy-messages";

export function payrollAmountView(amount: string, locale: Locale): string {
  const [whole = "0", fraction = ""] = amount.split(".");
  const integer = BigInt(whole);
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "IDR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .formatToParts(amount.startsWith("-") && integer === 0n ? -0 : integer)
    .map((part) => (part.type === "fraction" ? fraction.padEnd(2, "0") : part.value))
    .join("");
}
export function payrollPolicyView(policy: PayrollPolicy, locale: Locale) {
  const text = payrollPolicyMessages(locale);
  const month = new Intl.DateTimeFormat(locale, {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
  return {
    title: `${text.revision} ${policy.appliedRevision}`,
    properties: [
      { label: text.revision, value: String(policy.appliedRevision) },
      { label: text.from, value: month.format(new Date(`${policy.effectiveFrom}-01T00:00:00Z`)) },
      { label: text.until, value: month.format(new Date(`${policy.effectiveUntil}-01T00:00:00Z`)) },
      { label: text.taxRule, value: policy.incomeTaxRuleId },
      { label: text.insuranceRule, value: policy.insuranceRuleId },
      { label: text.minimum, value: payrollAmountView(policy.minimumMonthlyWage, locale) },
      { label: text.healthCap, value: payrollAmountView(policy.healthWageCap, locale) },
      { label: text.pensionCap, value: payrollAmountView(policy.pensionWageCap, locale) },
      { label: text.rounding, value: text[policy.contributionRounding] },
    ],
    references: [...new Set(policy.reviewReferences)],
  };
}
export function payrollPolicyHistoryView(
  review: PayrollPolicyReview,
  locale: Locale,
  timezone: string,
) {
  const text = payrollPolicyMessages(locale);
  const month = new Intl.DateTimeFormat(locale, {
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return review.history.items.map((revision) => ({
    id: String(revision.policy.appliedRevision),
    actionLabel: `${text.revision} ${revision.policy.appliedRevision}`,
    cells: [
      String(revision.policy.appliedRevision),
      month.format(new Date(`${revision.policy.effectiveFrom}-01T00:00:00Z`)),
      month.format(new Date(`${revision.policy.effectiveUntil}-01T00:00:00Z`)),
      time.format(new Date(revision.recordedAt)),
      revision.reason,
    ],
  }));
}
export function payrollPolicyEvidenceView(
  evidence: PayrollPolicyRevision,
  locale: Locale,
  timezone: string,
) {
  const text = payrollPolicyMessages(locale);
  return [
    { label: text.actor, value: evidence.actorId },
    {
      label: `${text.recorded} (${timezone})`,
      value: new Intl.DateTimeFormat(locale, {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: timezone,
      }).format(new Date(evidence.recordedAt)),
    },
    { label: text.reason, value: evidence.reason },
  ];
}
