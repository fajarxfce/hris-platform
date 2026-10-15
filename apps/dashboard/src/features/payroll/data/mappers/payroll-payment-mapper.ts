import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type {
  PayrollPaymentBatch,
  PayrollPaymentBatchId,
} from "../../domain/entities/payroll-payment-batch";
import type { PayrollPaymentSearch } from "../../domain/entities/payroll-payment-search";
import type { PayrollPaymentPage } from "../../domain/entities/payroll-payment-summary";
import { payrollPaymentAmountMinor } from "../../domain/policies/payroll-payment-amount";
import type { PayrollPaymentBatchDto, PayrollPaymentPageDto } from "../models/payroll-payment-dto";
import { payrollPaymentTotalMinor } from "./payroll-payment-amount";
export function toPayrollPaymentBatch(
  raw: PayrollPaymentBatchDto,
  companyId: CompanyId,
  id: PayrollPaymentBatchId,
): PayrollPaymentBatch {
  const released = raw.status === "RELEASED" || raw.status === "CLOSED";
  const items = raw.items.map((item) => {
    const settled = item.status === "SUCCEEDED" || item.status === "FAILED";
    const expectedVersion = settled ? 2 : item.status === "PREPARED" ? 0 : 1;
    if (
      (payrollPaymentAmountMinor(item.amount) ?? 0n) <= 0n ||
      item.version !== expectedVersion ||
      (item.observedPaymentVersion === null
        ? item.assessmentRevision !== 0
        : item.observedPaymentVersion < item.assessmentRevision) ||
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
      assessmentId: item.assessmentId.toLowerCase(),
      employmentId: item.employmentId.toLowerCase(),
      destination: Object.freeze({ ...item.destination }),
    });
  });
  const pending = items.filter((item) => item.status === "PENDING").length;
  if (
    raw.id.toLowerCase() !== id ||
    raw.itemCount !== items.length ||
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.assessmentId)).size !== items.length ||
    items.reduce((sum, item) => sum + (payrollPaymentAmountMinor(item.amount) ?? 0n), 0n) !==
      payrollPaymentTotalMinor(raw.totalAmount) ||
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
export function toPayrollPaymentPage(
  raw: PayrollPaymentPageDto,
  companyId: CompanyId,
  search: PayrollPaymentSearch,
): PayrollPaymentPage {
  let previous: { at: bigint; id: string } | null = null;
  const items = raw.items.map((row) => {
    const id = row.id.toLowerCase() as PayrollPaymentBatchId;
    const at = utcInstantMicroseconds(row.createdAt);
    const count = row.pendingCount + row.succeededCount + row.failedCount;
    if (
      at === null ||
      id === search.after ||
      (search.status !== null && row.status !== search.status) ||
      (previous && (at > previous.at || (at === previous.at && id >= previous.id))) ||
      payrollPaymentTotalMinor(row.totalAmount) <= 0n ||
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
