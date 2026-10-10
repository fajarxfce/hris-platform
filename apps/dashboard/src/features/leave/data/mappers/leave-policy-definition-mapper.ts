import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  LeavePolicyDefinition,
  LeavePolicyId,
} from "../../domain/entities/leave-policy-definition";
import type { LeavePolicyReview } from "../../domain/entities/leave-policy-review";
import type { LeavePolicyTerms } from "../../domain/entities/leave-policy-terms";
import type {
  LeavePolicyReviewDto,
  LeavePolicyTermsDto,
  LeaveTypeDto,
  LeaveTypePageDto,
} from "../models/leave-type-dto";

export function toLeavePolicyTerms(raw: LeavePolicyTermsDto): LeavePolicyTerms {
  if (new Set(raw.allowedContracts).size !== raw.allowedContracts.length)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    name: raw.name,
    paid: raw.paid,
    allowPartialDays: raw.allowPartialDays,
    minServiceMonths: raw.minServiceMonths,
    allowedContracts: Object.freeze([...raw.allowedContracts]),
    maxRequestDays: raw.maxRequestDays,
    attachmentRequired: raw.attachmentRequired,
    accrual: raw.accrual ? Object.freeze({ ...raw.accrual }) : null,
  });
}
export function toLeavePolicyDefinition(
  raw: LeaveTypeDto,
  companyId: CompanyId,
): LeavePolicyDefinition {
  if (
    !isCalendarDate(raw.effectiveFrom) ||
    raw.effectiveFrom < "1900-01-01" ||
    raw.effectiveFrom > "2200-12-31" ||
    raw.appliedRevision !== raw.version
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...toLeavePolicyTerms(raw),
    id: raw.id.toLowerCase() as LeavePolicyId,
    companyId,
    code: raw.code,
    effectiveFrom: raw.effectiveFrom,
    active: raw.active,
    version: raw.version,
  });
}
export function toLeavePolicyPage(
  raw: LeaveTypePageDto,
  company: CompanyId,
  active: boolean | null,
  after: string | null,
) {
  const items = raw.items.map((item) => toLeavePolicyDefinition(item, company));
  const last = items.at(-1);
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.code)).size !== items.length ||
    items.some((item) => item.code === after || (active !== null && item.active !== active)) ||
    (raw.nextCursor !== null &&
      (items.length !== 20 || raw.nextCursor !== last?.code || raw.nextCursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toLeavePolicyReview(
  raw: LeavePolicyReviewDto,
  company: CompanyId,
  id: LeavePolicyId,
  after: string | null,
): LeavePolicyReview {
  const current = toLeavePolicyDefinition(raw.current, company);
  if (current.id !== id || (after !== null && Number(after) > current.version))
    throw new InvalidHttpResponseError();
  let previous = after === null ? current.version + 1 : Number(after);
  const items = raw.history.items.map((row) => {
    if (
      row.revision !== previous - 1 ||
      !isCalendarDate(row.effectiveFrom) ||
      row.effectiveFrom < "1900-01-01" ||
      row.effectiveFrom > "2200-12-31" ||
      !Number.isFinite(Date.parse(row.recordedAt))
    )
      throw new InvalidHttpResponseError();
    previous = row.revision;
    return Object.freeze({
      ...toLeavePolicyTerms(row),
      revision: row.revision,
      effectiveFrom: row.effectiveFrom,
      active: row.active,
      actorId: row.actorId.toLowerCase(),
      reason: row.reason,
      recordedAt: row.recordedAt,
    });
  });
  const cursor = raw.history.nextCursor;
  if (
    (items.length === 0 && after !== "0") ||
    (cursor === null && items.length > 0 && items.at(-1)?.revision !== 0) ||
    (cursor !== null &&
      (items.length !== 20 ||
        cursor !== String(items.at(-1)?.revision) ||
        cursor === after ||
        cursor === "0"))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    current,
    history: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
  });
}
