import type { ExpenseDraftReferences } from "../../domain/entities/expense-draft-references";
import type { ExpenseLine } from "../../domain/entities/expense-line";

/** Retain labels only for the current draft, never every page the operator browsed. */
export function mergeExpenseDraftLabels(
  current: ExpenseDraftReferences,
  selected: ExpenseDraftReferences,
  lines: readonly ExpenseLine[],
): ExpenseDraftReferences {
  const categories = new Set(lines.map((line) => line.categoryId));
  const costCenters = new Set(
    lines.flatMap((line) => (line.costCenterId ? [line.costCenterId] : [])),
  );
  const receipts = new Set(lines.flatMap((line) => line.receiptRevisionIds));
  return Object.freeze({
    ...current,
    categories: Object.freeze(
      [
        ...new Map(
          [...current.categories, ...selected.categories].map((row) => [row.id, row]),
        ).values(),
      ].filter((row) => categories.has(row.id)),
    ),
    costCenters: Object.freeze(
      [
        ...new Map(
          [...current.costCenters, ...selected.costCenters].map((row) => [row.id, row]),
        ).values(),
      ].filter((row) => costCenters.has(row.id)),
    ),
    receipts: Object.freeze(
      [
        ...new Map(
          [...current.receipts, ...selected.receipts].map((row) => [row.revisionId, row]),
        ).values(),
      ].filter((row) => receipts.has(row.revisionId)),
    ),
  });
}
