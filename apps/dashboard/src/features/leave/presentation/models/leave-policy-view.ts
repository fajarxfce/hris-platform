import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeavePolicyPage } from "../../domain/entities/leave-policy-definition";
import type { LeavePolicyReview } from "../../domain/entities/leave-policy-review";
import type { LeavePolicyRevision } from "../../domain/entities/leave-policy-revision";
import type { LeavePolicyTerms } from "../../domain/entities/leave-policy-terms";
import { leaveMessages } from "../i18n/leave-messages";
import { leavePolicyMessages } from "../i18n/leave-policy-messages";

export function leavePolicyTermsView(policy: LeavePolicyTerms, locale: Locale) {
  const text = leaveMessages(locale);
  const own = leavePolicyMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return [
    { label: own.name, value: policy.name },
    { label: text.paid, value: policy.paid ? text.yes : text.no },
    { label: text.partial, value: policy.allowPartialDays ? text.yes : text.no },
    { label: text.months, value: number.format(policy.minServiceMonths) },
    { label: text.contracts, value: policy.allowedContracts.map((kind) => text[kind]).join(", ") },
    { label: text.maximum, value: number.format(policy.maxRequestDays) },
    { label: text.required, value: policy.attachmentRequired ? text.yes : text.no },
    { label: own.accrual, value: policy.accrual ? own[policy.accrual.frequency] : own.noAccrual },
    ...(policy.accrual
      ? [
          { label: own.perPeriod, value: number.format(Number(policy.accrual.daysPerPeriod)) },
          { label: own.carryLimit, value: number.format(Number(policy.accrual.carryLimitDays)) },
        ]
      : []),
  ];
}
export function leavePoliciesView(page: LeavePolicyPage, locale: Locale) {
  const text = leavePolicyMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((policy) => ({
    id: policy.id,
    actionLabel: policy.name,
    cells: [
      policy.code,
      policy.name,
      date.format(new Date(`${policy.effectiveFrom}T00:00:00Z`)),
      policy.active ? text.enabled : text.disabled,
      number.format(policy.version),
    ],
  }));
}
export function leavePolicyView(review: LeavePolicyReview, locale: Locale, timezone: string) {
  const text = leavePolicyMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  const current = review.current;
  return {
    properties: [
      { label: text.code, value: current.code },
      { label: text.version, value: number.format(current.version) },
      { label: text.from, value: date.format(new Date(`${current.effectiveFrom}T00:00:00Z`)) },
      { label: text.active, value: current.active ? text.enabled : text.disabled },
    ],
    terms: leavePolicyTermsView(current, locale),
    history: review.history.items.map((revision) => ({
      id: String(revision.revision),
      actionLabel: `${text.revision} ${revision.revision}`,
      cells: [
        number.format(revision.revision),
        date.format(new Date(`${revision.effectiveFrom}T00:00:00Z`)),
        revision.active ? text.enabled : text.disabled,
        time.format(new Date(revision.recordedAt)),
        revision.actorId,
        revision.reason,
      ],
    })),
  };
}
export function leavePolicyRevisionView(
  revision: LeavePolicyRevision,
  locale: Locale,
  timezone: string,
) {
  const text = leavePolicyMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return {
    title: `${text.revision} ${revision.revision}`,
    terms: leavePolicyTermsView(revision, locale),
    evidence: [
      { label: text.from, value: date.format(new Date(`${revision.effectiveFrom}T00:00:00Z`)) },
      { label: text.active, value: revision.active ? text.enabled : text.disabled },
      {
        label: `${text.recorded} (${timezone})`,
        value: time.format(new Date(revision.recordedAt)),
      },
      { label: text.actor, value: revision.actorId },
      { label: text.reason, value: revision.reason },
    ],
  };
}
