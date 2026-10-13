import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpensePaymentBatchId } from "../../domain/entities/expense-payment-batch";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
import type { ExpensePaymentReviewDto } from "../models/expense-payment-dto";
import { toExpensePaymentBatch } from "./expense-payment-mapper";
export function toExpensePaymentHistory(raw: ExpensePaymentReviewDto, after: string | null) {
  let expected = after === null ? 0 : Number(after) + 1;
  const items = raw.history.items.map((entry) => {
    if (
      entry.version !== expected++ ||
      entry.version > raw.batch.version ||
      (entry.version === 0 && (entry.kind !== "PREPARED" || entry.status !== "PREPARED")) ||
      (entry.version === 1 && !["RELEASED", "CANCELLED"].includes(entry.kind)) ||
      (entry.version === 1 && entry.status !== entry.kind) ||
      (entry.version > 1 &&
        (entry.kind !== "RECONCILED" || !["RELEASED", "CLOSED"].includes(entry.status))) ||
      (entry.version === raw.batch.version && entry.status !== raw.batch.status)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({ ...entry, actorId: entry.actorId.toLowerCase() });
  });
  const cursor = raw.history.nextCursor;
  if (
    (after !== null && Number(after) > raw.batch.version) ||
    (cursor !== null &&
      (items.length !== 20 ||
        cursor !== String(items.at(-1)?.version) ||
        Number(cursor) >= raw.batch.version)) ||
    (cursor === null && expected !== raw.batch.version + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
export function toExpensePaymentResults(raw: ExpensePaymentReviewDto) {
  const seen = new Set<string>(),
    references = new Set<string>();
  const items = new Map(raw.batch.items.map((item) => [item.id.toLowerCase(), item]));
  const results = raw.results.map((group, index) => {
    if (
      group.batchVersion !== index + 2 ||
      group.batchVersion > raw.batch.version ||
      group.resultingStatus !==
        (group.batchVersion === raw.batch.version ? raw.batch.status : "RELEASED")
    )
      throw new InvalidHttpResponseError();
    const entries = group.results.map((result) => {
      const itemId = result.itemId.toLowerCase(),
        item = items.get(itemId);
      if (
        !item ||
        seen.has(itemId) ||
        item.batchVersion !== group.batchVersion ||
        item.status !== result.status ||
        item.transactionReference !== result.transactionReference ||
        item.occurredAt !== result.occurredAt ||
        raw.batch.releasedAt === null ||
        Date.parse(result.occurredAt) < Date.parse(raw.batch.releasedAt) ||
        Date.parse(result.occurredAt) > Date.parse(group.recordedAt) ||
        (result.transactionReference !== null && references.has(result.transactionReference))
      )
        throw new InvalidHttpResponseError();
      seen.add(itemId);
      if (result.transactionReference !== null) references.add(result.transactionReference);
      return Object.freeze({ ...result, itemId });
    });
    return Object.freeze({
      ...group,
      actorId: group.actorId.toLowerCase(),
      results: Object.freeze(entries),
    });
  });
  const settled = raw.batch.items.filter((item) => ["SUCCEEDED", "FAILED"].includes(item.status));
  if (
    seen.size !== settled.length ||
    seen.size > 100 ||
    results.length !==
      (["RELEASED", "CLOSED"].includes(raw.batch.status) ? raw.batch.version - 1 : 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(results);
}
export function toExpensePaymentReview(
  raw: ExpensePaymentReviewDto,
  companyId: CompanyId,
  id: ExpensePaymentBatchId,
  after: string | null,
): ExpensePaymentReview {
  const batch = toExpensePaymentBatch(raw.batch, companyId, id);
  const history = toExpensePaymentHistory(raw, after);
  const results = toExpensePaymentResults(raw);
  const allowed =
    batch.status === "PREPARED"
      ? ["RELEASE", "CANCEL"]
      : batch.status === "RELEASED"
        ? batch.items.every((item) => item.status === "PENDING")
          ? ["RECONCILE", "EXPORT"]
          : ["RECONCILE"]
        : [];
  if (
    new Set(raw.availableActions).size !== raw.availableActions.length ||
    raw.availableActions.some((action) => !allowed.includes(action))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    batch,
    history,
    results,
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
