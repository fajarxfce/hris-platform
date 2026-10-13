import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { ExpenseClaim } from "../entities/expense-claim";
import type { ExpenseDraftChange } from "../entities/expense-draft-change";
import type { ExpenseDraftReferenceQuery } from "../entities/expense-draft-references";
import type { ExpenseLine } from "../entities/expense-line";
import { expenseAmountMinor } from "./expense-amount";
import { expenseEvidenceUnavailable } from "./expense-read-failure-policy";

export const canManageExpenseDrafts = (permissions: readonly string[]): boolean =>
  permissions.includes("expenses.manage");
export const canEditExpenseDraft = (claim: ExpenseClaim): boolean =>
  ["DRAFT", "RETURNED"].includes(claim.status) && claim.draftRevision < 99;

export function normalizeExpenseLine(line: ExpenseLine): ExpenseLine {
  return Object.freeze({
    ...line,
    id: line.id.toLowerCase(),
    categoryId: line.categoryId.toLowerCase(),
    occurredOn: line.occurredOn.trim(),
    amount: line.amount.trim(),
    description: line.description.trim(),
    costCenterId: line.costCenterId?.toLowerCase() ?? null,
    receiptRevisionIds: Object.freeze(line.receiptRevisionIds.map((id) => id.toLowerCase())),
  });
}
export function validateExpenseLine(line: ExpenseLine): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(line.id)) fields.id = "invalid_value";
  if (!isUuid(line.categoryId)) fields.categoryId = "required";
  if (
    !isCalendarDate(line.occurredOn) ||
    line.occurredOn < "1900-01-01" ||
    line.occurredOn > "2200-12-31"
  )
    fields.occurredOn = "invalid_date";
  const amount = expenseAmountMinor(line.amount);
  if (amount === null || amount <= 0n) fields.amount = "invalid_amount";
  if (!line.description.length || line.description.length > 500)
    fields.description = "invalid_description";
  if (line.costCenterId !== null && !isUuid(line.costCenterId))
    fields.costCenterId = "invalid_value";
  if (
    line.receiptRevisionIds.length > 3 ||
    line.receiptRevisionIds.some((id) => !isUuid(id)) ||
    new Set(line.receiptRevisionIds).size !== line.receiptRevisionIds.length
  )
    fields.receiptRevisionIds = "invalid_receipts";
  return Object.keys(fields).length
    ? { code: "invalid_expense_line", fields, parameters: {} }
    : null;
}
export function normalizeExpenseDraft(input: ExpenseDraftChange): ExpenseDraftChange {
  return Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    employmentId: input.employmentId.toLowerCase(),
    title: input.title.trim(),
    description: input.description.trim(),
    reason: input.reason.trim(),
    lines: Object.freeze(input.lines.map(normalizeExpenseLine)),
  });
}
export function validateExpenseDraft(input: ExpenseDraftChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.id)) fields.id = "invalid_value";
  if (!isUuid(input.employmentId)) fields.employmentId = "required";
  if (!input.title.length || input.title.length > 160) fields.title = "invalid_title";
  if (input.description.length > 2000) fields.description = "invalid_description";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_reason";
  if (input.lines.length > 20) fields.lines = "line_limit";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) || input.expectedVersion < 0)
  )
    fields.expectedVersion = "invalid_value";
  if (Object.keys(fields).length) return { code: "invalid_expense_draft", fields, parameters: {} };
  if (new Set(input.lines.map((line) => line.id)).size !== input.lines.length)
    return { code: "duplicate_expense_line", fields: {}, parameters: {} };
  for (const [index, line] of input.lines.entries()) {
    const failure = validateExpenseLine(line);
    if (failure) return { ...failure, parameters: { line: String(index + 1) } };
  }
  if (
    input.lines.reduce((sum, line) => sum + (expenseAmountMinor(line.amount) ?? 0n), 0n) >
    99999999999999n
  )
    return { code: "expense_claim_amount_limit", fields: {}, parameters: {} };
  return null;
}
export function expenseDraftReferenceQuery(
  lines: readonly ExpenseLine[],
): ExpenseDraftReferenceQuery {
  return Object.freeze({
    categoryIds: Object.freeze([...new Set(lines.map((line) => line.categoryId))]),
    costCenterIds: Object.freeze([
      ...new Set(lines.flatMap((line) => (line.costCenterId ? [line.costCenterId] : []))),
    ]),
    receiptRevisionIds: Object.freeze([
      ...new Set(lines.flatMap((line) => line.receiptRevisionIds)),
    ]),
  });
}
export const expenseDraftAccessLost = (failure: Failure): boolean =>
  expenseEvidenceUnavailable(failure) ||
  ["expense_access_denied", "employee_not_found", "expense_claim_not_found"].includes(failure.code);
/** A subsequent rejection does not prove that an earlier unconfirmed save failed. */
export const expenseDraftSaveWasRejected = (failure: Failure): boolean =>
  expenseDraftAccessLost(failure) ||
  [
    "invalid_expense_draft",
    "duplicate_expense_line",
    "invalid_expense_line",
    "expense_claim_amount_limit",
    "expense_identity_immutable",
    "stale_version",
    "expense_not_editable",
    "expense_draft_revision_limit",
    "expense_draft_capacity",
    "expense_category_unavailable",
    "expense_cost_center_unavailable",
    "expense_receipt_unavailable",
    "data_conflict",
    "invalid_version",
    "csrf_invalid",
    "company_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
