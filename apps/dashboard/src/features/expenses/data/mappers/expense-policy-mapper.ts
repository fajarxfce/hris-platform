import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  ExpensePolicyDefinition,
  ExpensePolicyId,
} from "../../domain/entities/expense-policy-definition";
import type { ExpensePolicyReview } from "../../domain/entities/expense-policy-review";
import type { ExpensePolicyTerms } from "../../domain/entities/expense-policy-terms";
import { expenseAmountMinor } from "../../domain/policies/expense-amount";
import type {
  ExpenseCategoryDto,
  ExpenseCategoryPageDto,
  ExpensePolicyReviewDto,
} from "../models/expense-policy-dto";

export function toExpensePolicyTerms(raw: ExpenseCategoryDto): ExpensePolicyTerms {
  const line = expenseAmountMinor(raw.maximumLineAmount),
    claim = expenseAmountMinor(raw.maximumClaimAmount);
  if (
    line === null ||
    claim === null ||
    line <= 0n ||
    claim < line ||
    new Set(raw.allowedContracts).size !== raw.allowedContracts.length ||
    !isCalendarDate(raw.effectiveFrom) ||
    raw.effectiveFrom < "1900-01-01" ||
    raw.effectiveFrom > "2200-12-31"
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    name: raw.name,
    maximumLineAmount: raw.maximumLineAmount,
    maximumClaimAmount: raw.maximumClaimAmount,
    receiptRequired: raw.receiptRequired,
    costCenterRequired: raw.costCenterRequired,
    maximumAgeDays: raw.maximumAgeDays,
    allowedContracts: Object.freeze([...raw.allowedContracts]),
  });
}

export function toExpensePolicyDefinition(
  raw: ExpenseCategoryDto,
  companyId: CompanyId,
): ExpensePolicyDefinition {
  if (raw.version !== raw.appliedRevision) throw new InvalidHttpResponseError();
  return Object.freeze({
    ...toExpensePolicyTerms(raw),
    id: raw.id.toLowerCase() as ExpensePolicyId,
    companyId,
    code: raw.code,
    effectiveFrom: raw.effectiveFrom,
    active: raw.active,
    version: raw.version,
  });
}

export function toExpensePolicyPage(
  raw: ExpenseCategoryPageDto,
  company: CompanyId,
  active: boolean | null,
  after: string | null,
) {
  const items = raw.items.map((item) => toExpensePolicyDefinition(item, company));
  let previous = after ?? "";
  for (const item of items) {
    if (item.code <= previous || (active !== null && item.active !== active))
      throw new InvalidHttpResponseError();
    previous = item.code;
  }
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (raw.nextCursor !== null && (items.length !== 20 || raw.nextCursor !== items.at(-1)?.code))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}

export function toExpensePolicyReview(
  raw: ExpensePolicyReviewDto,
  company: CompanyId,
  id: ExpensePolicyId,
  after: string | null,
): ExpensePolicyReview {
  const current = toExpensePolicyDefinition(raw.current, company);
  if (current.id !== id || (after !== null && Number(after) > current.version))
    throw new InvalidHttpResponseError();
  let previous = after === null ? -1 : Number(after);
  const items = raw.history.items.map((row) => {
    const revision = row.category;
    if (
      revision.id.toLowerCase() !== id ||
      revision.code !== current.code ||
      revision.version !== current.version ||
      revision.appliedRevision !== previous + 1 ||
      revision.appliedRevision > current.version ||
      !Number.isFinite(Date.parse(row.recordedAt))
    )
      throw new InvalidHttpResponseError();
    previous = revision.appliedRevision;
    return Object.freeze({
      ...toExpensePolicyTerms(revision),
      revision: revision.appliedRevision,
      effectiveFrom: revision.effectiveFrom,
      active: revision.active,
      actorId: row.actorId.toLowerCase(),
      reason: row.reason,
      recordedAt: row.recordedAt,
    });
  });
  const cursor = raw.history.nextCursor;
  if (
    (cursor === null && previous !== current.version) ||
    (cursor !== null &&
      (items.length !== 20 || cursor !== String(previous) || previous >= current.version))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    current,
    history: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
  });
}
