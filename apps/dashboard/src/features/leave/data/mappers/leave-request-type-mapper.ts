import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveRequestTypes } from "../../domain/entities/leave-request-type";
import type { LeaveTypePageDto } from "../models/leave-type-dto";

export function toLeaveRequestTypes(
  dto: LeaveTypePageDto,
  companyId: CompanyId,
  asOf: string,
  after: string | null,
): LeaveRequestTypes {
  let previous = after ?? "";
  const items = dto.items.map((type) => {
    if (
      type.code <= previous ||
      !type.active ||
      !isCalendarDate(type.effectiveFrom) ||
      type.effectiveFrom > asOf ||
      type.appliedRevision > type.version ||
      new Set(type.allowedContracts).size !== type.allowedContracts.length
    )
      throw new InvalidHttpResponseError();
    previous = type.code;
    return Object.freeze({
      companyId,
      asOf,
      id: type.id.toLowerCase(),
      code: type.code,
      name: type.name,
      allowPartialDays: type.allowPartialDays,
      attachmentRequired: type.attachmentRequired,
    });
  });
  if (
    new Set(items.map((type) => type.id)).size !== items.length ||
    (dto.nextCursor !== null && dto.nextCursor !== items.at(-1)?.code)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
