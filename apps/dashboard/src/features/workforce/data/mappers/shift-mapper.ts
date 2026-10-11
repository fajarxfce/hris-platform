import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  ShiftDefinition,
  ShiftDetails,
  ShiftId,
} from "../../domain/entities/shift-definition";
import type { ShiftPage } from "../../domain/entities/shift-page";
import type { ShiftSearch } from "../../domain/entities/shift-search";
import { isShiftCode, validateShiftDetails } from "../../domain/policies/shift-policy";
import type { ShiftDetailsDto, ShiftDto, ShiftPageDto } from "../models/shift-dto";

export function toShiftDetails(dto: ShiftDetailsDto): ShiftDetails {
  const details = Object.freeze({
    ...dto,
    startsAt: dto.startsAt.slice(0, 5),
    endsAt: dto.endsAt.slice(0, 5),
    fence: dto.fence ? Object.freeze({ ...dto.fence }) : null,
  });
  if (!validateShiftDetails(details).ok) throw new InvalidHttpResponseError();
  return details;
}

export function toShift(
  dto: ShiftDto,
  companyId: CompanyId,
  expectedId?: ShiftId,
): ShiftDefinition {
  const details = toShiftDetails(dto.details);
  const id = dto.id.toLowerCase() as ShiftId;
  if (expectedId !== undefined && id !== expectedId) throw new InvalidHttpResponseError();
  return Object.freeze({ id, companyId, details, active: dto.active, version: dto.version });
}

export function toShiftPage(dto: ShiftPageDto, company: CompanyId, search: ShiftSearch): ShiftPage {
  const items = dto.items.map((item) => toShift(item, company));
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.details.code)).size !== items.length ||
    items.some((item) => item.details.code === search.after) ||
    (dto.nextCursor !== null &&
      (!isShiftCode(dto.nextCursor) ||
        items.length !== 50 ||
        dto.nextCursor !== items.at(-1)?.details.code ||
        dto.nextCursor === search.after))
  )
    throw new InvalidHttpResponseError();
  // PostgreSQL owns code collation; JS lexical ordering is not an equivalent cursor check.
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
