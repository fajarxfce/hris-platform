import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { LeaveHistory } from "../../domain/entities/leave-history";
import type { LeaveHistoryDto } from "../models/leave-history-dto";

export function toLeaveHistory(
  dto: LeaveHistoryDto,
  version: number,
  after: string | null,
): LeaveHistory {
  let previous = after === null ? Infinity : Number(after);
  const items = dto.items.map((item) => {
    if (
      item.version >= previous ||
      item.version > version ||
      utcInstantMicroseconds(item.recordedAt) === null
    )
      throw new InvalidHttpResponseError();
    previous = item.version;
    return Object.freeze({
      ...item,
      actorId: item.actorId.toLowerCase() as AccountId,
      cancellationApprovalId: item.cancellationApprovalId?.toLowerCase() ?? null,
    });
  });
  if (
    dto.nextCursor !== null &&
    (items.length !== 20 ||
      dto.nextCursor !== String(items.at(-1)?.version) ||
      Number(dto.nextCursor) === 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
