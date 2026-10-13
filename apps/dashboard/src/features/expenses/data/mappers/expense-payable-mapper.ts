import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { ExpensePayablePage } from "../../domain/entities/expense-payable";
import type { ExpensePayableSearch } from "../../domain/entities/expense-payable-search";
import { expenseAmountMinor } from "../../domain/policies/expense-amount";
import type { ExpensePayablePageDto } from "../models/expense-payable-dto";
export function toExpensePayablePage(
  raw: ExpensePayablePageDto,
  companyId: CompanyId,
  search: ExpensePayableSearch,
): ExpensePayablePage {
  let previous: { at: bigint; id: string } | null = null;
  const items = raw.items.map((row) => {
    const id = row.claimId.toLowerCase(),
      at = utcInstantMicroseconds(row.approvedAt);
    if (
      at === null ||
      id === search.after ||
      (expenseAmountMinor(row.amount) ?? 0n) <= 0n ||
      (previous && (at > previous.at || (at === previous.at && id >= previous.id)))
    )
      throw new InvalidHttpResponseError();
    previous = { at, id };
    return Object.freeze({
      ...row,
      companyId,
      claimId: id,
      submissionId: row.submissionId.toLowerCase(),
      employmentId: row.employmentId.toLowerCase(),
      availableActions: Object.freeze([...row.availableActions]),
    });
  });
  const cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    new Set(items.map((item) => item.claimId)).size !== items.length ||
    new Set(items.map((item) => item.submissionId)).size !== items.length ||
    (cursor !== null &&
      (items.length !== 20 || cursor !== items.at(-1)?.claimId || cursor === search.after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
