import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  ExpenseDraftLabel,
  ExpenseDraftReferences,
} from "../../domain/entities/expense-draft-references";
import { expenseAmountMinor } from "../../domain/policies/expense-amount";
import { expenseDraftMessages } from "../i18n/expense-draft-messages";
import { expenseAmountView } from "./expense-amount-view";
import type { ExpenseDraftEditorState } from "./expense-draft-editor-state";
import type { ExpenseDraftOption, ExpenseDraftOptionPage } from "./expense-draft-option";
export function expenseDraftLabel(
  label: ExpenseDraftLabel | undefined,
  fallback: string,
  locale: Locale,
): string {
  if (!label?.name) return fallback;
  return `${label.code} · ${label.name}${label.active === false ? ` (${expenseDraftMessages(locale).archived})` : ""}`;
}
export function expenseDraftEditorView(state: ExpenseDraftEditorState, locale: Locale) {
  const text = expenseDraftMessages(locale),
    minor = state.lines.reduce((sum, line) => sum + (expenseAmountMinor(line.amount) ?? 0n), 0n);
  return {
    total: expenseAmountView(`${minor / 100n}.${String(minor % 100n).padStart(2, "0")}`, locale),
    rows: state.lines.map((line) => ({
      id: line.id,
      actionLabel: line.description,
      cells: [
        line.occurredOn,
        line.description,
        expenseDraftLabel(
          state.references?.categories.find((row) => row.id === line.categoryId),
          text.unavailableCategory,
          locale,
        ),
        expenseAmountView(line.amount, locale),
        String(line.receiptRevisionIds.length),
      ],
    })),
  };
}
export function expenseDraftOptionRows(
  page: ExpenseDraftOptionPage | null,
  selected: readonly string[],
  kind: ExpenseDraftOption["kind"],
  locale: Locale,
) {
  const text = expenseDraftMessages(locale);
  return (
    page?.items.map((option) => ({
      id: option.id,
      actionLabel: option.kind === "receipt" ? option.value.fileName : option.value.name,
      actionDisabled: selected.includes(option.id) || (kind === "receipt" && selected.length >= 3),
      cells:
        option.kind === "receipt"
          ? [
              option.value.title,
              `${option.value.fileName} · ${text.revision} ${option.value.revisionNumber}`,
            ]
          : [option.value.name, option.value.code],
    })) ?? []
  );
}
export function expenseLineSelectionView(
  category: string,
  costCenter: string | null,
  receipts: readonly string[],
  labels: ExpenseDraftReferences,
  locale: Locale,
) {
  const text = expenseDraftMessages(locale);
  return {
    category: category
      ? expenseDraftLabel(
          labels.categories.find((row) => row.id === category),
          text.unavailableCategory,
          locale,
        )
      : text.none,
    costCenter: costCenter
      ? expenseDraftLabel(
          labels.costCenters.find((row) => row.id === costCenter),
          text.unavailableCostCenter,
          locale,
        )
      : text.none,
    receipts: receipts.map((id) => {
      const label = labels.receipts.find((row) => row.revisionId === id);
      return {
        id,
        name: label?.fileName ?? text.unavailableReceipt,
        status: label?.status === "READY" ? "" : text.notReady,
      };
    }),
  };
}
