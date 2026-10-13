import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ExpensePolicyPage } from "../../domain/entities/expense-policy-definition";
import type { ExpensePolicyReview } from "../../domain/entities/expense-policy-review";
import type { ExpensePolicyRevision } from "../../domain/entities/expense-policy-revision";
import type { ExpensePolicyTerms } from "../../domain/entities/expense-policy-terms";
import { expensePolicyMessages } from "../i18n/expense-policy-messages";

/** Format validated decimal amounts without converting money to a binary float. */
export function expenseAmountView(amount: string, locale: Locale): string {
  const [whole = "0", fraction = ""] = amount.split(".");
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "IDR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .formatToParts(BigInt(whole))
    .map((part) => (part.type === "fraction" ? fraction.padEnd(2, "0") : part.value))
    .join("");
}

export function expensePolicyTermsView(policy: ExpensePolicyTerms, locale: Locale) {
  const text = expensePolicyMessages(locale);
  return [
    { label: text.name, value: policy.name },
    { label: text.maximumLine, value: expenseAmountView(policy.maximumLineAmount, locale) },
    { label: text.maximumClaim, value: expenseAmountView(policy.maximumClaimAmount, locale) },
    { label: text.maximumAge, value: new Intl.NumberFormat(locale).format(policy.maximumAgeDays) },
    { label: text.receipt, value: policy.receiptRequired ? text.yes : text.no },
    { label: text.costCenter, value: policy.costCenterRequired ? text.yes : text.no },
    { label: text.contracts, value: policy.allowedContracts.map((kind) => text[kind]).join(", ") },
  ];
}

export function expensePoliciesView(page: ExpensePolicyPage, locale: Locale) {
  const text = expensePolicyMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return page.items.map((policy) => ({
    id: policy.id,
    actionLabel: policy.name,
    cells: [
      policy.code,
      policy.name,
      expenseAmountView(policy.maximumLineAmount, locale),
      date.format(new Date(`${policy.effectiveFrom}T00:00:00Z`)),
      policy.active ? text.enabled : text.disabled,
      new Intl.NumberFormat(locale).format(policy.version),
    ],
  }));
}

export function expensePolicyView(review: ExpensePolicyReview, locale: Locale, timezone: string) {
  const text = expensePolicyMessages(locale);
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
    terms: expensePolicyTermsView(current, locale),
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

export function expensePolicyRevisionView(
  revision: ExpensePolicyRevision,
  locale: Locale,
  timezone: string,
) {
  const text = expensePolicyMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return {
    title: `${text.revision} ${revision.revision}`,
    terms: expensePolicyTermsView(revision, locale),
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
