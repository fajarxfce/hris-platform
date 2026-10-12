import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  DocumentRetention,
  DocumentRetentionChange,
  DocumentRetentionReview,
} from "../../domain/entities/document-retention";
import { documentRetentionLifecycleMessages } from "../i18n/document-retention-lifecycle-messages";
import { documentRetentionPeriod } from "./document-retention-view";

export function documentRetentionProperties(
  state: DocumentRetention,
  locale: Locale,
  timezone: string,
) {
  const text = documentRetentionLifecycleMessages(locale),
    archive = state.archive;
  const date = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  const items: { label: string; value: string }[] = [
    { label: text.reference, value: state.documentId },
    { label: text.status, value: archive ? text.archived : text.active },
    { label: text.hold, value: state.legalHold ? text.held : text.notHeld },
    { label: text.version, value: state.version === null ? text.unchanged : String(state.version) },
  ];
  if (archive)
    items.push(
      {
        label: text.archivedAt,
        value: `${date.format(new Date(archive.archivedAt))} (${timezone})`,
      },
      { label: text.retention, value: documentRetentionPeriod(archive.retentionDays, locale) },
      {
        label: text.eligible,
        value: archive.eligibleAt
          ? `${date.format(new Date(archive.eligibleAt))} (${timezone})`
          : text.indefinite,
      },
      {
        label: text.policy,
        value: archive.policyId
          ? `${archive.policyId} / ${archive.policyVersion}`
          : text.unconfigured,
      },
    );
  return items;
}
export function documentRetentionHistoryRows(
  review: DocumentRetentionReview,
  locale: Locale,
  timezone: string,
) {
  const text = documentRetentionLifecycleMessages(locale),
    date = new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
  return review.history.items.map((change) => ({
    id: String(change.state.version),
    actionLabel: `${text.version} ${change.state.version}`,
    cells: [
      String(change.state.version),
      text.actionLabels[change.action],
      `${date.format(new Date(change.recordedAt))} (${timezone})`,
      change.reason,
    ],
  }));
}
export function documentRetentionChangeProperties(
  change: DocumentRetentionChange,
  locale: Locale,
  timezone: string,
) {
  const text = documentRetentionLifecycleMessages(locale),
    date = new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
  return [
    { label: text.action, value: text.actionLabels[change.action] },
    { label: text.recorded, value: `${date.format(new Date(change.recordedAt))} (${timezone})` },
    { label: text.actor, value: change.actorId },
    { label: text.reason, value: change.reason },
    ...documentRetentionProperties(change.state, locale, timezone),
    ...(change.retiredRevisionId
      ? [{ label: text.revision, value: change.retiredRevisionId }]
      : []),
  ];
}
