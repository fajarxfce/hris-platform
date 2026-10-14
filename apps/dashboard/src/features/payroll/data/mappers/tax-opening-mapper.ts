import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { TaxOpening, TaxOpeningSummary } from "../../domain/entities/tax-opening";
import type { TaxOpeningChange } from "../../domain/entities/tax-opening-change";
import type {
  TaxOpeningEmployees,
  TaxOpeningReview,
  TaxOpeningSearch,
} from "../../domain/entities/tax-opening-review";
import {
  normalizeTaxOpeningTerms,
  validateTaxOpeningTerms,
} from "../../domain/policies/tax-opening-validation";
import type {
  TaxOpeningChangeDto,
  TaxOpeningDto,
  TaxOpeningEmployeesDto,
  TaxOpeningReviewDto,
  TaxOpeningSummaryDto,
} from "../models/tax-opening-dto";
export function toTaxOpeningSummary(
  raw: TaxOpeningSummaryDto,
  employee: string,
  year: string,
): TaxOpeningSummary {
  if (
    raw.employeeId.toLowerCase() !== employee ||
    String(raw.year) !== year ||
    (raw.status === "VERIFIED") !== (raw.verifiedBy !== null) ||
    raw.verifiedBy?.toLowerCase() === raw.preparedBy.toLowerCase() ||
    !Number.isFinite(Date.parse(raw.recordedAt))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    year,
    id: raw.id.toLowerCase(),
    employeeId: employee,
    preparedBy: raw.preparedBy.toLowerCase(),
    verifiedBy: raw.verifiedBy?.toLowerCase() ?? null,
  });
}
export function toTaxOpening(
  raw: TaxOpeningDto,
  company: CompanyId,
  employee: string,
  year: string,
): TaxOpening {
  const { terms, ...summary } = raw;
  if (validateTaxOpeningTerms(year, terms)) throw new InvalidHttpResponseError();
  return Object.freeze({
    ...toTaxOpeningSummary(summary, employee, year),
    companyId: company,
    terms: normalizeTaxOpeningTerms(terms),
  });
}
export function toTaxOpeningEmployees(
  raw: TaxOpeningEmployeesDto,
  search: TaxOpeningSearch,
): TaxOpeningEmployees {
  const items = raw.items.map((item) => {
    if (
      (item.openingVersion === null) !== (item.openingStatus === null) ||
      (search.status !== null && (item.openingStatus ?? "UNPREPARED") !== search.status)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({ ...item, id: item.id.toLowerCase() });
  });
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.employeeNumber)).size !== items.length ||
    items.some((item) => item.employeeNumber === search.after) ||
    (raw.nextCursor !== null &&
      (items.length !== 50 ||
        raw.nextCursor !== items.at(-1)?.employeeNumber ||
        raw.nextCursor === search.after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toTaxOpeningReview(
  raw: TaxOpeningReviewDto,
  company: CompanyId,
  employee: string,
  year: string,
  after: string | null,
): TaxOpeningReview {
  if (
    String(raw.year) !== year ||
    raw.employee.id.toLowerCase() !== employee ||
    new Set(raw.availableActions).size !== raw.availableActions.length
  )
    throw new InvalidHttpResponseError();
  const current = raw.current ? toTaxOpening(raw.current, company, employee, year) : null;
  if (
    (after !== null && (!current || Number(after) > current.version)) ||
    (!current && (raw.history.items.length || raw.history.nextCursor !== null))
  )
    throw new InvalidHttpResponseError();
  let previous = after === null ? -1 : Number(after);
  const items = raw.history.items.map((row) => {
    const value = toTaxOpeningSummary(row, employee, year);
    if (
      value.version !== previous + 1 ||
      value.version > (current?.version ?? -1) ||
      value.id !== current?.id
    )
      throw new InvalidHttpResponseError();
    if (
      value.version === current?.version &&
      (value.status !== current.status ||
        value.preparedBy !== current.preparedBy ||
        value.verifiedBy !== current.verifiedBy ||
        value.reason !== current.reason ||
        value.recordedAt !== current.recordedAt)
    )
      throw new InvalidHttpResponseError();
    previous = value.version;
    return value;
  });
  const next = raw.history.nextCursor;
  if (
    (next === null && previous !== (current?.version ?? -1)) ||
    (next !== null &&
      (items.length !== 20 || next !== String(previous) || previous >= (current?.version ?? -1)))
  )
    throw new InvalidHttpResponseError();
  if (
    (raw.availableActions.length && (raw.frozen || current?.version === 999)) ||
    (raw.availableActions.includes("VERIFY") && current?.status !== "DRAFT")
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    year: year,
    employee: Object.freeze({ ...raw.employee, id: employee }),
    current,
    history: Object.freeze({ items: Object.freeze(items), nextCursor: next }),
    frozen: raw.frozen,
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
export function toTaxOpeningChangeDto(input: TaxOpeningChange): TaxOpeningChangeDto {
  return {
    expectedEmploymentVersion: input.expectedEmploymentVersion,
    expectedVersion: input.expectedVersion,
    reason: input.reason,
    terms: { ...input.terms, history: { ...input.terms.history } },
  };
}
