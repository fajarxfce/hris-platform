import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ApprovalInbox, ApprovalRequest } from "../../domain/entities/approval-request";
import { approvalMessages } from "../i18n/approval-messages";

export function approvalInboxView(page: ApprovalInbox, locale: Locale, timezone: string) {
  const text = approvalMessages(locale);
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return page.items.map((request) => ({
    id: request.id,
    actionLabel: `${text[request.kind]} · ${request.resourceId}`,
    cells: [
      text[request.kind],
      request.resourceId,
      text[request.status],
      time.format(new Date(request.submittedAt)),
    ],
  }));
}
export function approvalRequestView(request: ApprovalRequest, locale: Locale, timezone: string) {
  const text = approvalMessages(locale);
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  return {
    properties: [
      { label: text.kind, value: text[request.kind] },
      { label: text.resource, value: request.resourceId },
      { label: text.status, value: text[request.status] },
      {
        label: `${text.submitted} (${timezone})`,
        value: time.format(new Date(request.submittedAt)),
      },
      { label: text.author, value: request.authorId },
      { label: text.requester, value: request.requesterId ?? text.none },
      { label: text.version, value: number.format(request.version) },
      { label: text.template, value: request.templateId },
      { label: text.templateRevision, value: number.format(request.templateRevision) },
    ],
    stages: request.stages.map((stage, index) => {
      const status =
        index < request.currentStep || request.status === "APPROVED"
          ? text.completed
          : index === request.currentStep
            ? request.status === "PENDING"
              ? text.waiting
              : request.status === "BLOCKED"
                ? text.assignmentRequired
                : text[request.status]
            : request.status === "CANCELLED" || request.status === "REJECTED"
              ? text.notReached
              : text.upcoming;
      return {
        id: String(index),
        cells: [number.format(index + 1), stage.assignees.join(", ") || text.none, status],
      };
    }),
  };
}
