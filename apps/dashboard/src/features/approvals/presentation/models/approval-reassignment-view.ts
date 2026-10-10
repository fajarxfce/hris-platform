import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ApprovalRequest } from "../../domain/entities/approval-request";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalReassignmentMessages } from "../i18n/approval-reassignment-messages";

export function approvalReassignmentView(request: ApprovalRequest, locale: Locale) {
  const text = approvalMessages(locale);
  const reassign = approvalReassignmentMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return [
    { label: text.kind, value: text[request.kind] },
    { label: text.resource, value: request.resourceId },
    { label: text.status, value: text[request.status] },
    { label: text.version, value: number.format(request.version) },
    { label: reassign.stage, value: number.format(request.currentStep + 1) },
    {
      label: reassign.assigned,
      value: request.stages[request.currentStep]?.assignees.join(", ") || text.none,
    },
  ];
}
