import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type {
  AudienceGroup,
  AudienceGroupId,
  AudienceGroupPage,
} from "../../domain/entities/audience-group";
import type { AudienceGroupDto, AudienceGroupPageDto } from "../models/audience-group-dto";

export function toAudienceGroup(
  dto: AudienceGroupDto,
  id: AudienceGroupId,
  revision: number | null,
): AudienceGroup {
  const employmentIds = dto.employmentIds.map((value) => value.toLowerCase()).sort();
  if (
    dto.id.toLowerCase() !== id ||
    (revision !== null && dto.version !== revision) ||
    new Set(employmentIds).size !== employmentIds.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id,
    version: dto.version,
    name: dto.name,
    active: dto.active,
    memberCount: employmentIds.length,
    employmentIds: Object.freeze(employmentIds),
    recordedAt: dto.recordedAt,
    recordedBy: dto.recordedBy.toLowerCase(),
    reason: dto.reason,
  });
}
export function toAudienceGroupPage(
  dto: AudienceGroupPageDto,
  after: string | null,
): AudienceGroupPage {
  let previous = after;
  const items = dto.items.map((item) => {
    const id = item.id.toLowerCase() as AudienceGroupId;
    if (previous !== null && id <= previous) throw new InvalidHttpResponseError();
    previous = id;
    return Object.freeze({ ...item, id });
  });
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (nextCursor !== null && (items.length !== 50 || nextCursor !== items.at(-1)?.id))
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
