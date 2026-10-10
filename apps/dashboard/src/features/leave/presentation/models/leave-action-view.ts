import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type LeaveRequestIntent,
  leaveRequestIntents,
} from "../../domain/entities/leave-request-action";
import type { LeaveRequestDetails } from "../../domain/entities/leave-request-details";
import { canPerformLeaveAction } from "../../domain/policies/leave-request-action-policy";
import { leaveActionMessages } from "../i18n/leave-action-messages";
import { leaveMessages } from "../i18n/leave-messages";
import { leaveRequestView } from "./leave-request-view";

export function leaveActionCopy(
  intent: LeaveRequestIntent,
  phase: "request" | "cancellation",
  locale: Locale,
) {
  const text = leaveActionMessages(locale);
  const cancellation = phase === "cancellation";
  return {
    approve: {
      title: cancellation ? text.approveCancellation : text.approve,
      hint: cancellation ? text.cancellationHint : text.decisionHint,
    },
    reject: {
      title: cancellation ? text.rejectCancellation : text.reject,
      hint: cancellation ? text.rejectionHint : text.rejectHint,
    },
    withdraw: {
      title: cancellation ? text.withdrawCancellation : text.withdraw,
      hint: cancellation ? text.withdrawCancellationHint : text.withdrawHint,
    },
    cancel: { title: text.cancel, hint: text.requestHint },
  }[intent];
}
export function leaveActionLinks(
  access: CompanyAccess,
  request: LeaveRequestDetails,
  locale: Locale,
) {
  return leaveRequestIntents
    .filter((intent) => canPerformLeaveAction(access, request, intent))
    .map((intent) => ({
      intent,
      label: leaveActionCopy(
        intent,
        request.status === "CANCELLATION_PENDING" ? "cancellation" : "request",
        locale,
      ).title,
    }));
}
export function leaveActionView(request: LeaveRequestDetails, locale: Locale, timezone: string) {
  const text = leaveMessages(locale);
  const view = leaveRequestView(request, locale, timezone);
  const workflow =
    request.status === "CANCELLATION_PENDING" ? request.cancellation : request.approval;
  return {
    properties: view.properties,
    days: view.days,
    workflow: workflow
      ? [
          {
            label: leaveActionMessages(locale).process,
            value: request.status === "CANCELLATION_PENDING" ? text.cancellation : text.initial,
          },
          { label: text.status, value: text[workflow.status] },
          {
            label: text.stage,
            value: ["PENDING", "BLOCKED"].includes(workflow.status)
              ? new Intl.NumberFormat(locale).format(workflow.currentStep + 1)
              : text.none,
          },
        ]
      : [],
  };
}
