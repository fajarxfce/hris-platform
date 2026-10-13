import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseCategoryPage } from "../../domain/entities/expense-category";
import type { ExpenseCategoryPageDto } from "../models/expense-policy-dto";
import { toExpensePolicyTerms } from "./expense-policy-mapper";
export function toExpenseCategoryPage(
  raw: ExpenseCategoryPageDto,
  companyId: CompanyId,
  asOf: string,
  after: string | null,
): ExpenseCategoryPage {
  let previous = after ?? "";
  const items = raw.items.map((row) => {
    if (
      !row.active ||
      row.code <= previous ||
      row.effectiveFrom > asOf ||
      row.appliedRevision > row.version
    )
      throw new InvalidHttpResponseError();
    previous = row.code;
    return Object.freeze({
      ...toExpensePolicyTerms(row),
      companyId,
      id: row.id.toLowerCase(),
      code: row.code,
      effectiveFrom: row.effectiveFrom,
      version: row.version,
      appliedRevision: row.appliedRevision,
    });
  });
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (raw.nextCursor !== null && (items.length !== 20 || raw.nextCursor !== items.at(-1)?.code))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
