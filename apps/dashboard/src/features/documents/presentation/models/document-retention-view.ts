import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  type DocumentRetentionPolicy,
  type DocumentRetentionPolicyReview,
  type DocumentRetentionPolicySelection,
  retentionClassifications,
} from "../../domain/entities/document-retention-policy";
import { documentMessages } from "../i18n/document-messages";
import { documentRetentionMessages } from "../i18n/document-retention-messages";

export function documentRetentionPeriod(days: number | null, locale: Locale): string {
  const text = documentRetentionMessages(locale);
  return days === null
    ? text.indefinite
    : new Intl.NumberFormat(locale, { style: "unit", unit: "day", unitDisplay: "long" }).format(
        days,
      );
}
export function documentRetentionPolicyRows(
  policies: readonly DocumentRetentionPolicy[],
  locale: Locale,
) {
  const text = documentRetentionMessages(locale),
    categories = documentMessages(locale);
  return retentionClassifications.map((classification) => {
    const policy = policies.find((entry) => entry.classification === classification);
    return {
      id: classification,
      actionLabel: categories[classification],
      cells: [
        categories[classification],
        policy ? documentRetentionPeriod(policy.retentionDays, locale) : text.notConfigured,
        policy ? String(policy.version) : "—",
      ],
    };
  });
}
export function documentRetentionPolicyProperties(
  selection: DocumentRetentionPolicySelection,
  locale: Locale,
  timezone: string,
) {
  const text = documentRetentionMessages(locale),
    policy = selection.policy;
  const items: { label: string; value: string }[] = [
    { label: text.classification, value: documentMessages(locale)[selection.classification] },
    {
      label: text.retention,
      value: policy ? documentRetentionPeriod(policy.retentionDays, locale) : text.notConfigured,
    },
  ];
  if (policy) {
    const date = new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
    items.push(
      { label: text.version, value: String(policy.version) },
      { label: text.recorded, value: `${date.format(new Date(policy.recordedAt))} (${timezone})` },
      { label: text.actor, value: policy.actorId },
      { label: text.reason, value: policy.reason },
    );
  }
  return items;
}
export function documentRetentionPolicyHistoryRows(
  review: DocumentRetentionPolicyReview,
  locale: Locale,
  timezone: string,
) {
  const date = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  return review.history.items.map((policy) => ({
    id: `${policy.id}:${policy.version}`,
    cells: [
      String(policy.version),
      documentRetentionPeriod(policy.retentionDays, locale),
      `${date.format(new Date(policy.recordedAt))} (${timezone})`,
      policy.reason,
      policy.actorId,
    ],
  }));
}
