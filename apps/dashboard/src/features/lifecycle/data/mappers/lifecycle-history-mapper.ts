import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { LifecycleHistoryPage } from "../../domain/entities/lifecycle-event";
import { isLifecycleHistoryCursor } from "../../domain/policies/lifecycle-case-policy";
import { isLifecycleTaskKey } from "../../domain/policies/lifecycle-template-policy";
import type { LifecycleHistoryPageDto } from "../models/lifecycle-event-dto";

export function toLifecycleHistory(
  dto: LifecycleHistoryPageDto,
  after: number | null,
): LifecycleHistoryPage {
  if (
    dto.items.some(
      (item, index) =>
        !item.reason.trim() ||
        utcInstantMicroseconds(item.recordedAt) === null ||
        (item.taskKey !== null && !isLifecycleTaskKey(item.taskKey)) ||
        item.version <= (index === 0 ? (after ?? -1) : (dto.items[index - 1]?.version ?? -1)),
    ) ||
    (dto.nextCursor !== null &&
      (!isLifecycleHistoryCursor(dto.nextCursor) ||
        dto.items.length !== 50 ||
        dto.nextCursor !== String(dto.items.at(-1)?.version)))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    items: Object.freeze(
      dto.items.map((item) =>
        Object.freeze({
          version: item.version,
          taskKey: item.taskKey,
          action: item.action,
          assigneeId:
            item.assigneeId === null ? null : (item.assigneeId.toLowerCase() as AccountId),
          actorId: item.actorId.toLowerCase() as AccountId,
          reason: item.reason,
          recordedAt: item.recordedAt,
        }),
      ),
    ),
    nextCursor: dto.nextCursor,
  });
}
