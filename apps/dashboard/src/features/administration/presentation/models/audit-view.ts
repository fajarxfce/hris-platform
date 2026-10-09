import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AuditEvent } from "../../domain/entities/audit-event";
import type { AuditPage } from "../../domain/entities/audit-page";
import { administrationMessages } from "../i18n/administration-messages";

export function auditPageView(page: AuditPage, locale: Locale) {
  const dates = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: "UTC",
  });
  return {
    window: `${dates.format(new Date(page.from))} – ${dates.format(new Date(page.until))} UTC`,
    rows: page.items.map((event) => ({
      id: event.id,
      cells: [
        dates.format(new Date(event.recordedAt)),
        event.action,
        `${event.resourceType} / ${event.resourceId.slice(0, 8)}`,
        event.actorId.slice(0, 8),
      ],
    })),
  };
}

export function auditEventView(event: AuditEvent, locale: Locale) {
  const text = administrationMessages(locale);
  return [
    { label: text.id, value: event.id },
    { label: text.recordedAt, value: event.recordedAt },
    { label: text.action, value: event.action },
    { label: text.actor, value: event.actorId },
    { label: text.resourceType, value: event.resourceType },
    { label: text.resourceId, value: event.resourceId },
    { label: text.company, value: event.companyId },
    { label: text.correlation, value: event.correlationId },
  ];
}

export type AuditPageView = ReturnType<typeof auditPageView>;
export type AuditEventView = ReturnType<typeof auditEventView>;
