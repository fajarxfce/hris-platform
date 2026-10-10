import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type {
  EmploymentHistoryPage,
  EmploymentRevision,
} from "../../domain/entities/employment-revision";
import type {
  EmploymentHistoryPageDto,
  EmploymentRevisionDto,
} from "../models/employment-revision-dto";
import { toEmploymentTerms } from "./employment-terms-mapper";

export function toEmploymentRevision(dto: EmploymentRevisionDto): EmploymentRevision {
  const recorded = utcInstantMicroseconds(dto.recordedAt);
  const cancelled = dto.cancellation ? utcInstantMicroseconds(dto.cancellation.recordedAt) : null;
  if (
    recorded === null ||
    dto.reason.trim().length === 0 ||
    (dto.cancellation !== null &&
      (cancelled === null || dto.cancellation.reason.trim().length === 0))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    revision: dto.revision,
    terms: toEmploymentTerms(dto.terms),
    reason: dto.reason,
    recordedAt: dto.recordedAt,
    cancellation: dto.cancellation === null ? null : Object.freeze({ ...dto.cancellation }),
  });
}

export function toEmploymentHistoryPage(
  dto: EmploymentHistoryPageDto,
  after: string | null,
): EmploymentHistoryPage {
  const items = dto.items.map(toEmploymentRevision);
  let previous = after === null ? -1 : Number(after);
  for (const item of items) {
    if (item.revision <= previous) throw new InvalidHttpResponseError();
    previous = item.revision;
  }
  if (
    dto.nextCursor !== null &&
    (items.length !== 50 || dto.nextCursor !== String(items.at(-1)?.revision))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
