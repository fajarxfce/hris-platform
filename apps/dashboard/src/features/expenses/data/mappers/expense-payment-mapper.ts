import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type {
  ExpensePaymentBatch,
  ExpensePaymentBatchId,
} from "../../domain/entities/expense-payment-batch";
import type { ExpensePaymentSearch } from "../../domain/entities/expense-payment-search";
import type { ExpensePaymentPage } from "../../domain/entities/expense-payment-summary";
import { expenseAmountMinor } from "../../domain/policies/expense-amount";
import type { ExpensePaymentBatchDto, ExpensePaymentPageDto } from "../models/expense-payment-dto";
import { expensePaymentTotalMinor } from "./expense-payment-amount";
export function toExpensePaymentBatch(
  raw: ExpensePaymentBatchDto,
  companyId: CompanyId,
  id: ExpensePaymentBatchId,
): ExpensePaymentBatch {
  const released = raw.status === "RELEASED" || raw.status === "CLOSED";
  const items = raw.items.map((item) => {
    const settled = item.status === "SUCCEEDED" || item.status === "FAILED";
    const expectedVersion = settled ? 2 : item.status === "PREPARED" ? 0 : 1;
    if (
      (expenseAmountMinor(item.amount) ?? 0n) <= 0n ||
      item.version !== expectedVersion ||
      item.batchVersion > raw.version ||
      (settled ? item.batchVersion < 2 : item.batchVersion !== expectedVersion) ||
      (item.status === "SUCCEEDED") !== (item.transactionReference !== null) ||
      settled !== (item.occurredAt !== null) ||
      (raw.status === "PREPARED" && item.status !== "PREPARED") ||
      (raw.status === "CANCELLED" && item.status !== "CANCELLED") ||
      (released && !["PENDING", "SUCCEEDED", "FAILED"].includes(item.status))
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...item,
      id: item.id.toLowerCase(),
      claimId: item.claimId.toLowerCase(),
      submissionId: item.submissionId.toLowerCase(),
      employmentId: item.employmentId.toLowerCase(),
      destination: Object.freeze({ ...item.destination }),
    });
  });
  const pending = items.filter((item) => item.status === "PENDING").length;
  if (
    raw.id.toLowerCase() !== id ||
    raw.itemCount !== items.length ||
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.submissionId)).size !== items.length ||
    new Set(items.map((item) => item.claimId)).size !== items.length ||
    items.reduce((sum, item) => sum + (expenseAmountMinor(item.amount) ?? 0n), 0n) !==
      expensePaymentTotalMinor(raw.totalAmount) ||
    released !== (raw.releasedBy !== null) ||
    released !== (raw.releasedAt !== null) ||
    (raw.status === "PREPARED" && raw.version !== 0) ||
    (raw.status === "CANCELLED" && raw.version !== 1) ||
    (released && raw.version < 1) ||
    (raw.status === "RELEASED" && pending === 0) ||
    (raw.status === "CLOSED" && (pending > 0 || raw.version < 2))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id,
    companyId,
    createdBy: raw.createdBy.toLowerCase(),
    releasedBy: raw.releasedBy?.toLowerCase() ?? null,
    items: Object.freeze(items),
  });
}
export function toExpensePaymentPage(
  raw: ExpensePaymentPageDto,
  companyId: CompanyId,
  search: ExpensePaymentSearch,
): ExpensePaymentPage {
  let previous: { at: bigint; id: string } | null = null;
  const items = raw.items.map((row) => {
    const id = row.id.toLowerCase() as ExpensePaymentBatchId;
    const at = utcInstantMicroseconds(row.createdAt);
    const count = row.pendingCount + row.succeededCount + row.failedCount;
    if (
      at === null ||
      id === search.after ||
      (search.status !== null && row.status !== search.status) ||
      (previous && (at > previous.at || (at === previous.at && id >= previous.id))) ||
      expensePaymentTotalMinor(row.totalAmount) <= 0n ||
      count > row.itemCount ||
      (["PREPARED", "CANCELLED"].includes(row.status) && count !== 0) ||
      (["RELEASED", "CLOSED"].includes(row.status) && count !== row.itemCount) ||
      (row.status === "RELEASED" && row.pendingCount === 0) ||
      (row.status === "CLOSED" && row.pendingCount !== 0)
    )
      throw new InvalidHttpResponseError();
    previous = { at, id };
    return Object.freeze({ ...row, id, companyId, createdBy: row.createdBy.toLowerCase() });
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
