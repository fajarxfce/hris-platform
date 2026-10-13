import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseCostCenterPage } from "../../domain/entities/expense-cost-center";
import type { ExpenseDraftDirectory } from "../../domain/entities/expense-draft-employee";
import type { ExpenseDraftReceiptPage } from "../../domain/entities/expense-draft-receipt-option";
import type {
  ExpenseDraftReferenceQuery,
  ExpenseDraftReferences,
} from "../../domain/entities/expense-draft-references";
import type {
  ExpenseCostCenterPageDto,
  ExpenseDraftDirectoryDto,
  ExpenseDraftReceiptPageDto,
  ExpenseDraftReferencesDto,
} from "../models/expense-drafting-dto";
export function toExpenseDraftDirectory(
  raw: ExpenseDraftDirectoryDto,
  companyId: CompanyId,
  after: string | null,
): ExpenseDraftDirectory {
  if (
    !isCalendarDate(raw.companyDate) ||
    raw.companyDate < "1900-01-01" ||
    raw.companyDate > "2200-12-31"
  )
    throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: raw.timezone }).format(0);
  } catch {
    throw new InvalidHttpResponseError();
  }
  let previous = after ?? "";
  const items = raw.employees.items.map((item) => {
    if (item.employeeNumber <= previous) throw new InvalidHttpResponseError();
    previous = item.employeeNumber;
    return Object.freeze({ ...item, id: item.id.toLowerCase(), companyId });
  });
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (raw.employees.nextCursor !== null &&
      (items.length !== 20 || raw.employees.nextCursor !== items.at(-1)?.employeeNumber))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyDate: raw.companyDate,
    timezone: raw.timezone,
    employees: Object.freeze({ items: Object.freeze(items), nextCursor: raw.employees.nextCursor }),
  });
}
export function toExpenseDraftReceiptPage(
  raw: ExpenseDraftReceiptPageDto,
  companyId: CompanyId,
  employmentId: string,
  after: string | null,
): ExpenseDraftReceiptPage {
  let previous = after ?? "";
  const items = raw.items.map((item) =>
    Object.freeze({
      ...item,
      companyId,
      employmentId,
      documentId: item.documentId.toLowerCase(),
      revisionId: item.revisionId.toLowerCase(),
    }),
  );
  for (const item of items) {
    if (item.documentId <= previous) throw new InvalidHttpResponseError();
    previous = item.documentId;
  }
  if (
    new Set(items.map((item) => item.documentId)).size !== items.length ||
    new Set(items.map((item) => item.revisionId)).size !== items.length ||
    items.some(
      (item) => item.documentId === after || !Number.isFinite(Date.parse(item.validatedAt)),
    ) ||
    (raw.nextCursor !== null &&
      (items.length !== 20 || raw.nextCursor.toLowerCase() !== items.at(-1)?.documentId))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    items: Object.freeze(items),
    nextCursor: raw.nextCursor?.toLowerCase() ?? null,
  });
}
export function toExpenseCostCenterPage(
  raw: ExpenseCostCenterPageDto,
  companyId: CompanyId,
  after: string | null,
): ExpenseCostCenterPage {
  let previous = after ?? "";
  const items = raw.items.map((item) => {
    if (`COST_CENTER:${item.code}` <= previous) throw new InvalidHttpResponseError();
    previous = `COST_CENTER:${item.code}`;
    return Object.freeze({ ...item, id: item.id.toLowerCase(), companyId });
  });
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (raw.nextCursor !== null &&
      (items.length !== 20 || raw.nextCursor !== `COST_CENTER:${items.at(-1)?.code}`))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toExpenseDraftReferences(
  raw: ExpenseDraftReferencesDto,
  companyId: CompanyId,
  employmentId: string,
  query: ExpenseDraftReferenceQuery,
): ExpenseDraftReferences {
  if (raw.employmentId.toLowerCase() !== employmentId) throw new InvalidHttpResponseError();
  for (const [rows, requested] of [
    [raw.categories, query.categoryIds],
    [raw.costCenters, query.costCenterIds],
  ] as const) {
    const ids = new Set(rows.map((row) => row.id.toLowerCase()));
    if (
      ids.size !== rows.length ||
      ids.size !== requested.length ||
      requested.some((id) => !ids.has(id))
    )
      throw new InvalidHttpResponseError();
    for (const row of rows)
      if (
        (row.code === null) !== (row.name === null) ||
        (row.active === null) !== (row.name === null)
      )
        throw new InvalidHttpResponseError();
  }
  const receiptIds = new Set(raw.receipts.map((row) => row.revisionId.toLowerCase()));
  if (
    receiptIds.size !== raw.receipts.length ||
    receiptIds.size !== query.receiptRevisionIds.length ||
    query.receiptRevisionIds.some((id) => !receiptIds.has(id))
  )
    throw new InvalidHttpResponseError();
  for (const receipt of raw.receipts) {
    const metadata = [
      receipt.documentId,
      receipt.fileName,
      receipt.mediaType,
      receipt.size,
      receipt.status,
    ];
    if (metadata.some((value) => value === null) && metadata.some((value) => value !== null))
      throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    companyId,
    employmentId,
    categories: Object.freeze(
      raw.categories.map((row) => Object.freeze({ ...row, id: row.id.toLowerCase() })),
    ),
    costCenters: Object.freeze(
      raw.costCenters.map((row) => Object.freeze({ ...row, id: row.id.toLowerCase() })),
    ),
    receipts: Object.freeze(
      raw.receipts.map((row) =>
        Object.freeze({
          ...row,
          revisionId: row.revisionId.toLowerCase(),
          documentId: row.documentId?.toLowerCase() ?? null,
        }),
      ),
    ),
  });
}
