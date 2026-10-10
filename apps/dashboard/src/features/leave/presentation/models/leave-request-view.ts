import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeaveAttachment } from "../../domain/entities/leave-attachment";
import type { LeaveRequestPage } from "../../domain/entities/leave-request";
import type { LeaveRequestDetails } from "../../domain/entities/leave-request-details";
import { leaveMessages } from "../i18n/leave-messages";

export function leaveRequestsView(page: LeaveRequestPage, locale: Locale) {
  const text = leaveMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((request) => ({
    id: request.id,
    actionLabel: `${request.employeeName} · ${request.id}`,
    cells: [
      request.employeeName,
      request.employeeNumber,
      request.typeName,
      date.format(new Date(`${request.from}T00:00:00Z`)),
      date.format(new Date(`${request.until}T00:00:00Z`)),
      number.format(Number(request.chargedDays)),
      text[request.status],
    ],
  }));
}

export function leaveRequestView(request: LeaveRequestDetails, locale: Locale, timezone: string) {
  const text = leaveMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const policy = request.policy;
  return {
    properties: [
      { label: text.employee, value: request.employeeName },
      { label: text.number, value: request.employeeNumber },
      { label: text.type, value: `${policy.name} · ${policy.code}` },
      { label: text.status, value: text[request.status] },
      { label: text.days, value: number.format(Number(request.chargedDays)) },
      {
        label: `${text.submitted} (${timezone})`,
        value: time.format(new Date(request.submittedAt)),
      },
      { label: text.version, value: number.format(request.version) },
      { label: text.reason, value: request.reason },
    ],
    policy: [
      { label: text.policyVersion, value: number.format(policy.revision) },
      { label: text.paid, value: policy.paid ? text.yes : text.no },
      { label: text.partial, value: policy.allowPartialDays ? text.yes : text.no },
      { label: text.months, value: number.format(policy.minServiceMonths) },
      {
        label: text.contracts,
        value: policy.allowedContracts.map((contract) => text[contract]).join(", "),
      },
      { label: text.maximum, value: number.format(policy.maxRequestDays) },
      { label: text.required, value: policy.attachmentRequired ? text.yes : text.no },
    ],
    days: request.days.map((day) => ({
      id: day.workDate,
      cells: [
        date.format(new Date(`${day.workDate}T00:00:00Z`)),
        text[day.portion],
        time.format(new Date(day.startsAt)),
        time.format(new Date(day.endsAt)),
        number.format(day.plannedMinutes),
        number.format(day.chargedMinutes),
      ],
    })),
    history: request.history.items.map((change) => ({
      id: String(change.version),
      cells: [
        number.format(change.version),
        text[change.kind],
        text[change.status],
        time.format(new Date(change.recordedAt)),
        change.actorId,
        change.reason || text.none,
      ],
    })),
    workflows: [
      { title: text.initial, workflow: request.approval },
      ...(request.cancellation
        ? [{ title: text.cancellation, workflow: request.cancellation }]
        : []),
    ].map(({ title, workflow }) => ({
      id: workflow.id,
      title,
      status: text[workflow.status],
      rows: workflow.stages.map((stage, index) => ({
        id: String(index),
        cells: [
          number.format(index + 1),
          stage.join(", ") || text.none,
          index === workflow.currentStep && ["PENDING", "BLOCKED"].includes(workflow.status)
            ? text.active
            : text.none,
        ],
      })),
    })),
  };
}

export function leaveAttachmentRows(attachments: readonly LeaveAttachment[], locale: Locale) {
  const number = new Intl.NumberFormat(locale);
  return attachments.map((attachment) => ({
    id: attachment.revisionId,
    actionLabel: attachment.fileName,
    cells: [attachment.fileName, attachment.mediaType, number.format(attachment.size)],
  }));
}
