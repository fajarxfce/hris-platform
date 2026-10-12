import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  DocumentEmployeeDirectory,
  DocumentEmployeeSearch,
} from "../../domain/entities/document-employee";
import type { DocumentEmployeeDirectoryDto } from "../models/document-dto";

export function toDocumentEmployees(
  raw: DocumentEmployeeDirectoryDto,
  company: CompanyId,
  search: DocumentEmployeeSearch,
  employmentId: string | null,
): DocumentEmployeeDirectory {
  if (
    raw.companyId.toLowerCase() !== company ||
    raw.query !== search.query ||
    !isCalendarDate(raw.asOf)
  )
    throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: raw.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  const items = raw.employees.items.map((item) =>
    Object.freeze({ ...item, id: item.id.toLowerCase(), companyId: company }),
  );
  let previous = search.after ?? "";
  for (const item of items) {
    if (item.employeeNumber <= previous || (employmentId !== null && item.id !== employmentId))
      throw new InvalidHttpResponseError();
    previous = item.employeeNumber;
  }
  const cursor = raw.employees.nextCursor;
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (employmentId !== null && (items.length > 1 || cursor !== null)) ||
    (cursor !== null &&
      (items.length !== 20 || cursor !== items.at(-1)?.employeeNumber || cursor === search.after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    asOf: raw.asOf,
    timezone: raw.timezone,
    query: raw.query,
    employees: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
  });
}
