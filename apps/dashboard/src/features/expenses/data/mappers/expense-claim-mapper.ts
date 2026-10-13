import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaim, ExpenseClaimId } from "../../domain/entities/expense-claim";
import type { ExpenseClaimSearch } from "../../domain/entities/expense-claim-search";
import type { ExpenseClaimPage } from "../../domain/entities/expense-claim-summary";
import type { ExpenseLine } from "../../domain/entities/expense-line";
import type { ExpenseSubmissionId } from "../../domain/entities/expense-submission-summary";
import { expenseAmountMinor } from "../../domain/policies/expense-amount";
import type {
  ExpenseClaimDto,
  ExpenseClaimPageDto,
  ExpenseLineDto,
} from "../models/expense-claim-dto";
export function toExpenseLine(raw: ExpenseLineDto): ExpenseLine {
  const amount = expenseAmountMinor(raw.amount);
  const receipts = raw.receiptRevisionIds.map((id) => id.toLowerCase());
  if (
    amount === null ||
    amount <= 0n ||
    raw.occurredOn < "1900-01-01" ||
    raw.occurredOn > "2200-12-31" ||
    new Set(receipts).size !== receipts.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase(),
    categoryId: raw.categoryId.toLowerCase(),
    costCenterId: raw.costCenterId?.toLowerCase() ?? null,
    receiptRevisionIds: Object.freeze(receipts),
  });
}
export function toExpenseClaim(
  raw: ExpenseClaimDto,
  companyId: CompanyId,
  id: ExpenseClaimId,
): ExpenseClaim {
  const lines = raw.draft.lines.map(toExpenseLine);
  const total = lines.reduce((sum, line) => sum + (expenseAmountMinor(line.amount) ?? 0n), 0n);
  if (
    raw.id.toLowerCase() !== id ||
    raw.draftRevision !== raw.draft.revision ||
    raw.draftRevision > raw.version ||
    new Set(lines.map((line) => line.id)).size !== lines.length ||
    total !== expenseAmountMinor(raw.draft.totalAmount) ||
    (raw.submissionCount === 0) !== (raw.latestSubmissionId === null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id,
    companyId,
    employmentId: raw.employmentId.toLowerCase(),
    createdBy: raw.createdBy.toLowerCase(),
    latestSubmissionId:
      (raw.latestSubmissionId?.toLowerCase() as ExpenseSubmissionId | undefined) ?? null,
    draft: Object.freeze({
      ...raw.draft,
      actorId: raw.draft.actorId.toLowerCase(),
      lines: Object.freeze(lines),
    }),
  });
}
export function toExpenseClaimPage(
  raw: ExpenseClaimPageDto,
  companyId: CompanyId,
  search: ExpenseClaimSearch,
): ExpenseClaimPage {
  let previous: { at: string; id: string } | null = null;
  const items = raw.items.map((row) => {
    const id = row.id.toLowerCase() as ExpenseClaimId;
    if (
      id === search.after ||
      (search.status !== null && row.status !== search.status) ||
      (search.employmentId !== null && row.employmentId.toLowerCase() !== search.employmentId) ||
      (previous &&
        (Date.parse(row.createdAt) > Date.parse(previous.at) ||
          (row.createdAt === previous.at && id >= previous.id)))
    )
      throw new InvalidHttpResponseError();
    previous = { at: row.createdAt, id };
    return Object.freeze({ ...row, id, companyId, employmentId: row.employmentId.toLowerCase() });
  });
  const cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (cursor !== null &&
      (items.length !== 20 || cursor !== items.at(-1)?.id || cursor === search.after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
