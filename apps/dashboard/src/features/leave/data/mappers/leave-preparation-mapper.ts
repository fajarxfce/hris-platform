import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { LeaveEvidenceOptions } from "../../domain/entities/leave-evidence-option";
import type { LeavePreparationDirectory } from "../../domain/entities/leave-preparation-employee";
import type { LeaveEvidenceOptionsDto } from "../models/leave-evidence-option-dto";
import type { LeavePreparationDirectoryDto } from "../models/leave-preparation-employee-dto";

export function toLeavePreparationDirectory(
  dto: LeavePreparationDirectoryDto,
  companyId: CompanyId,
  after: string | null,
): LeavePreparationDirectory {
  if (!isCalendarDate(dto.companyDate)) throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: dto.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  let previous = after ?? "";
  const items = dto.employees.items.map((item) => {
    if (item.employeeNumber <= previous) throw new InvalidHttpResponseError();
    previous = item.employeeNumber;
    return Object.freeze({ ...item, id: item.id.toLowerCase(), companyId });
  });
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (dto.employees.nextCursor !== null && dto.employees.nextCursor !== items.at(-1)?.employeeNumber)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyDate: dto.companyDate,
    timezone: dto.timezone,
    employees: Object.freeze({ items: Object.freeze(items), nextCursor: dto.employees.nextCursor }),
  });
}

export function toLeaveEvidenceOptions(
  dto: LeaveEvidenceOptionsDto,
  companyId: CompanyId,
  employeeId: string,
  after: string | null,
): LeaveEvidenceOptions {
  let previous = after ?? "";
  const items = dto.items.map((item) => {
    const documentId = item.documentId.toLowerCase();
    if (documentId <= previous || utcInstantMicroseconds(item.validatedAt) === null)
      throw new InvalidHttpResponseError();
    previous = documentId;
    return Object.freeze({
      ...item,
      documentId,
      revisionId: item.revisionId.toLowerCase(),
      companyId,
      employeeId,
    });
  });
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    new Set(items.map((item) => item.revisionId)).size !== items.length ||
    (nextCursor !== null && nextCursor !== items.at(-1)?.documentId)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
