import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollReviewApproval } from "../../domain/entities/payroll-review-approval";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";

export function payrollReviewStagesView(
  approval: PayrollReviewApproval,
  names: ReadonlyMap<string, string>,
  locale: Locale,
) {
  const text = payrollReviewMessages(locale);
  return approval.stages.map((stage, index) => ({
    id: String(index),
    cells: [
      String(index + 1),
      stage.map((id) => names.get(id) ?? id).join(", ") || text.none,
      index < approval.currentStep || approval.status === "APPROVED"
        ? text.completed
        : index === approval.currentStep
          ? approval.status === "PENDING"
            ? text.waiting
            : approval.status === "BLOCKED"
              ? text.unassigned
              : text.statuses[approval.status]
          : approval.status === "CANCELLED" || approval.status === "REJECTED"
            ? text.notReached
            : text.upcoming,
    ],
  }));
}
