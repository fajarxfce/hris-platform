import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeaveCarryExpiry } from "../../domain/entities/leave-carry-expiry";
import type { LeaveCarryExpiryDetails } from "../../domain/entities/leave-carry-expiry-details";
import { leaveCarryExpiryMessages } from "../i18n/leave-carry-expiry-messages";
export function leaveCarryExpiriesView(items: readonly LeaveCarryExpiry[], locale: Locale) {
  const text = leaveCarryExpiryMessages(locale),
    number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return items.map((item) => ({
    id: item.id,
    actionLabel: `${item.typeName} · ${item.year}`,
    cells: [
      item.typeName,
      String(item.year),
      date.format(new Date(`${item.validThrough}T00:00:00Z`)),
      text[item.status],
      `${number.format(item.completedEmployees)} / ${number.format(item.totalEmployees)}`,
      text[item.sourceKind],
    ],
  }));
}
export function leaveCarryExpiryView(details: LeaveCarryExpiryDetails, locale: Locale) {
  const text = leaveCarryExpiryMessages(locale),
    expiry = details.expiry;
  const number = new Intl.NumberFormat(locale);
  const instant = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: expiry.timezone,
  });
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return {
    properties: [
      { label: text.type, value: expiry.typeName },
      { label: text.year, value: String(expiry.year) },
      {
        label: text.validThrough,
        value: date.format(new Date(`${expiry.validThrough}T00:00:00Z`)),
      },
      { label: text.status, value: text[details.job.status] },
      {
        label: text.processed,
        value: `${number.format(expiry.completedEmployees)} / ${number.format(expiry.totalEmployees)}`,
      },
      { label: text.scheduled, value: instant.format(new Date(details.job.scheduledFor)) },
      { label: text.timezone, value: expiry.timezone },
      { label: text.source, value: text[expiry.sourceKind] },
      { label: text.reason, value: expiry.reason },
    ],
    rows: details.results.items.map((item) => ({
      id: item.employee.id,
      actionLabel: item.employee.name ?? item.employee.number ?? text.noName,
      cells: [
        item.employee.name ?? text.noName,
        item.employee.number ?? "—",
        text[item.status],
        number.format(Number(item.expiredDays)),
        instant.format(new Date(item.recordedAt)),
      ],
    })),
    attempts: details.attempts.map((item) => ({
      id: item.jobId,
      cells: [
        number.format(item.number),
        instant.format(new Date(item.startedAt)),
        number.format(item.baseCompleted),
        item.reason,
      ],
    })),
    issue: details.job.failureCode
      ? ({ code: details.job.failureCode, fields: {}, parameters: {} } as Failure)
      : null,
  };
}
