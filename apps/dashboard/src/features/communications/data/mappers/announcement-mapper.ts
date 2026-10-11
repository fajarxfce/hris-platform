import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  Announcement,
  AnnouncementId,
  AnnouncementSummary,
} from "../../domain/entities/announcement";
import type { AnnouncementPage } from "../../domain/entities/announcement-page";
import type {
  AnnouncementDto,
  AnnouncementPageDto,
  AnnouncementSummaryDto,
} from "../models/announcement-dto";

export function toAnnouncementSummary(
  dto: AnnouncementSummaryDto,
  companyId: CompanyId,
): AnnouncementSummary {
  if (
    !dto.title.trim() ||
    (dto.audienceKind === "COMPANY" ? dto.targetCount !== 0 : dto.targetCount < 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: dto.id.toLowerCase() as AnnouncementId,
    companyId,
    version: dto.version,
    title: dto.title,
    audienceKind: dto.audienceKind,
    targetCount: dto.targetCount,
    acknowledgementRequired: dto.acknowledgementRequired,
    status: dto.status,
    recordedAt: dto.recordedAt,
    publicationJobId: dto.publicationJobId?.toLowerCase() ?? null,
    scheduledFor: dto.scheduledFor,
    publishedAt: dto.publishedAt,
    recipientCount: dto.recipientCount,
    publicationAttempts: dto.publicationAttempts,
  });
}
export function toAnnouncement(
  dto: AnnouncementDto,
  company: CompanyId,
  id: AnnouncementId,
  version: number | null,
): Announcement {
  const ids = dto.audience.targetIds.map((target) => target.toLowerCase());
  if (
    dto.id.toLowerCase() !== id ||
    (version !== null && dto.version !== version) ||
    new Set(ids).size !== ids.length ||
    !dto.body.trim() ||
    !dto.reason.trim()
  )
    throw new InvalidHttpResponseError();
  const summary = toAnnouncementSummary(
    { ...dto, audienceKind: dto.audience.kind, targetCount: ids.length },
    company,
  );
  return Object.freeze({
    ...summary,
    body: dto.body,
    targetIds: Object.freeze(ids),
    recordedBy: dto.recordedBy.toLowerCase(),
    reason: dto.reason,
  });
}
export function toAnnouncementPage(
  dto: AnnouncementPageDto,
  company: CompanyId,
  after: string | null,
  historyId: AnnouncementId | null,
): AnnouncementPage {
  const items = dto.items.map((item) => toAnnouncementSummary(item, company));
  const keys = items.map((item) => (historyId === null ? item.id : String(item.version)));
  if (
    new Set(keys).size !== keys.length ||
    items.some((item) => historyId !== null && item.id !== historyId)
  )
    throw new InvalidHttpResponseError();
  let previous = after;
  for (const key of keys) {
    if (
      previous !== null &&
      (historyId === null ? key <= previous : Number(key) <= Number(previous))
    )
      throw new InvalidHttpResponseError();
    previous = key;
  }
  if (
    dto.nextCursor !== null &&
    (items.length !== 50 || dto.nextCursor.toLowerCase() !== keys.at(-1))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    items: Object.freeze(items),
    nextCursor: dto.nextCursor?.toLowerCase() ?? null,
  });
}
