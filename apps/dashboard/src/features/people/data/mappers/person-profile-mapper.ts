import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type {
  PersonId,
  PersonProfile,
  PersonProfileChange,
} from "../../domain/entities/person-profile";
import type {
  PersonProfileHistoryPage,
  PersonProfileRevision,
} from "../../domain/entities/person-profile-revision";
import type {
  PersonProfileChangeDto,
  PersonProfileDto,
  PersonProfileHistoryDto,
  PersonProfileReceiptDto,
} from "../models/person-profile-dto";

export function toPersonProfile(dto: PersonProfileDto): PersonProfile {
  if (!dto.legalName.trim() || (dto.birthDate !== null && !isCalendarDate(dto.birthDate)))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    personId: dto.personId.toLowerCase() as PersonId,
    ownerCompanyId: dto.ownerCompanyId.toLowerCase() as CompanyId,
    accountId: (dto.accountId?.toLowerCase() as AccountId) ?? null,
  });
}

export function toPersonProfileHistory(
  dto: PersonProfileHistoryDto,
  after: string | null,
): PersonProfileHistoryPage {
  let previous = after === null ? -1 : Number(after);
  const items = dto.items.map((item): PersonProfileRevision => {
    if (
      item.revision <= previous ||
      !item.legalName.trim() ||
      !item.reason.trim() ||
      (item.birthDate !== null && !isCalendarDate(item.birthDate)) ||
      utcInstantMicroseconds(item.recordedAt) === null
    )
      throw new InvalidHttpResponseError();
    previous = item.revision;
    return Object.freeze({
      ...item,
      accountId: (item.accountId?.toLowerCase() as AccountId) ?? null,
      actorId: (item.actorId?.toLowerCase() as AccountId) ?? null,
    });
  });
  if (
    dto.nextCursor !== null &&
    (items.length !== 50 || dto.nextCursor !== String(items.at(-1)?.revision))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}

export function toPersonProfileChangeDto(change: PersonProfileChange): PersonProfileChangeDto {
  return {
    expectedVersion: change.expectedVersion,
    legalName: change.legalName,
    birthDate: change.birthDate,
    nationality: change.nationality,
    email: change.email,
    reason: change.reason,
  };
}
export function toPersonProfileReceipt(
  dto: PersonProfileReceiptDto,
  change: Pick<PersonProfileChange, "personId" | "expectedVersion">,
): MutationReceipt {
  if (dto.id.toLowerCase() !== change.personId || dto.version !== change.expectedVersion + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
