import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OvertimePage } from "../../domain/entities/overtime-page";
import type { OvertimeRequestDetails } from "../../domain/entities/overtime-request-details";
import { overtimeMessages } from "../i18n/overtime-messages";

export function overtimeRows(page: OvertimePage, locale: Locale) {
  const text = overtimeMessages(locale),
    number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  return page.items.map((item) => ({
    id: item.id,
    actionLabel: `${item.employeeNumber}, ${date.format(new Date(`${item.workDate}T00:00:00Z`))}`,
    cells: [
      date.format(new Date(`${item.workDate}T00:00:00Z`)),
      `${item.employeeName} · ${item.employeeNumber}`,
      text[item.status],
      number.format(item.requested.workedMinutes),
      number.format(item.approvedMinutes),
    ],
  }));
}

export function overtimeDetailsView(
  details: OvertimeRequestDetails,
  selected: string | null,
  locale: Locale,
) {
  const { request, approval, history } = details,
    text = overtimeMessages(locale),
    number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  const instant = new Intl.DateTimeFormat(locale, {
    timeZone: request.timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  const change = history.items.find((item) => String(item.revision) === selected);
  return {
    properties: [
      { label: text.employee, value: request.employeeName },
      { label: text.employeeNumber, value: request.employeeNumber },
      { label: text.date, value: date.format(new Date(`${request.workDate}T00:00:00Z`)) },
      { label: text.status, value: text[request.status] },
      { label: text.approved, value: number.format(request.approvedMinutes) },
      { label: text.timezone, value: request.timezone },
      { label: text.reason, value: request.reason },
    ],
    intervals: [
      { id: "requested", label: text.requestedTime, value: request.requested },
      ...(request.actual ? [{ id: "actual", label: text.actualTime, value: request.actual }] : []),
    ].map((item) => ({
      id: item.id,
      cells: [
        item.label,
        instant.format(new Date(item.value.startsAt)),
        instant.format(new Date(item.value.endsAt)),
        number.format(item.value.breakMinutes),
        number.format(item.value.workedMinutes),
      ],
    })),
    schedule: [
      {
        label: text.schedule,
        value:
          request.schedule.kind === "WORK"
            ? `${request.schedule.shift.details.name} · ${instant.format(new Date(request.schedule.startsAt))} – ${instant.format(new Date(request.schedule.endsAt))}`
            : text.dayOff,
      },
    ],
    approval: approval
      ? [
          { label: text.status, value: text[approval.status] },
          {
            label: text.stage,
            value: `${number.format(approval.currentStep + 1)} / ${number.format(approval.stages.length)}`,
          },
        ]
      : null,
    history: history.items.map((item) => ({
      id: String(item.revision),
      actionLabel: number.format(item.revision),
      cells: [
        number.format(item.revision),
        text[item.kind],
        text[item.status],
        instant.format(new Date(item.recordedAt)),
      ],
    })),
    change: change
      ? [
          { label: text.revision, value: number.format(change.revision) },
          { label: text.kind, value: text[change.kind] },
          { label: text.status, value: text[change.status] },
          { label: text.recorded, value: instant.format(new Date(change.recordedAt)) },
          { label: text.timezone, value: request.timezone },
          { label: text.actor, value: change.actorId },
          { label: text.reason, value: change.reason || "—" },
          {
            label: text.start,
            value: change.actual ? instant.format(new Date(change.actual.startsAt)) : "—",
          },
          {
            label: text.end,
            value: change.actual ? instant.format(new Date(change.actual.endsAt)) : "—",
          },
          {
            label: text.break,
            value: change.actual ? number.format(change.actual.breakMinutes) : "—",
          },
          {
            label: text.actual,
            value: change.actual ? number.format(change.actual.workedMinutes) : "—",
          },
          { label: text.approved, value: number.format(change.approvedMinutes) },
        ]
      : null,
  };
}
