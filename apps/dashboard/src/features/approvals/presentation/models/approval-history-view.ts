import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ApprovalHistory } from "../../domain/entities/approval-history";
import { approvalHistoryMessages } from "../i18n/approval-history-messages";
import { approvalRequestView } from "./approval-view";
export function approvalHistoryView(history: ApprovalHistory, locale: Locale, timezone: string) {
  const text = approvalHistoryMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "medium",
  });
  const number = new Intl.NumberFormat(locale);
  return {
    properties: approvalRequestView(history.request, locale, timezone).properties,
    decisions: history.decisions.map((entry) => ({
      id: String(entry.step),
      cells: [
        number.format(entry.step + 1),
        text[entry.decision],
        entry.actorId,
        entry.decidingFor === entry.actorId ? text.none : entry.decidingFor,
        date.format(new Date(entry.decidedAt)),
        entry.reason || text.none,
      ],
    })),
    reassignments: history.reassignments.items.map((entry) => ({
      id: String(entry.revision),
      cells: [
        number.format(entry.revision),
        number.format(entry.step + 1),
        entry.assignees.join(", "),
        entry.actorId,
        date.format(new Date(entry.recordedAt)),
        entry.reason || text.none,
      ],
    })),
  };
}
