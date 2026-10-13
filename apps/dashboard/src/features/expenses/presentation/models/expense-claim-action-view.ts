import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ExpenseClaimActionReview } from "../../domain/entities/expense-claim-action-review";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import { expenseDraftMessages } from "../i18n/expense-draft-messages";
import { expenseAmountView } from "./expense-amount-view";
import { expenseDraftLabel, expenseLineSelectionView } from "./expense-draft-view";
export function expenseClaimActionView(
  review: ExpenseClaimActionReview,
  selectedId: string | null,
  locale: Locale,
) {
  const { claim, references } = review,
    text = expenseClaimMessages(locale),
    draft = expenseDraftMessages(locale);
  const selected = claim.draft.lines.find((line) => line.id === selectedId);
  return {
    title: claim.draft.title,
    properties: [
      { label: text.employee, value: claim.draft.employeeName },
      { label: text.employeeNumber, value: claim.draft.employeeNumber },
      { label: text.status, value: text[claim.status] },
      { label: text.total, value: expenseAmountView(claim.draft.totalAmount, locale) },
      { label: text.version, value: String(claim.version) },
      { label: text.submissions, value: String(claim.submissionCount) },
    ],
    lines: references
      ? claim.draft.lines.map((line) => ({
          id: line.id,
          actionLabel: line.description,
          cells: [
            line.occurredOn,
            line.description,
            expenseDraftLabel(
              references.categories.find((row) => row.id === line.categoryId),
              draft.unavailableCategory,
              locale,
            ),
            expenseAmountView(line.amount, locale),
            String(line.receiptRevisionIds.length),
          ],
        }))
      : [],
    item:
      selected && references
        ? {
            description: selected.description,
            amount: expenseAmountView(selected.amount, locale),
            occurredOn: selected.occurredOn,
            ...expenseLineSelectionView(
              selected.categoryId,
              selected.costCenterId,
              selected.receiptRevisionIds,
              references,
              locale,
            ),
          }
        : null,
  };
}
