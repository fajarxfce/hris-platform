import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { Announcement } from "../../domain/entities/announcement";
import type { AnnouncementAudiencePreview } from "../../domain/entities/announcement-audience-preview";
import type { AnnouncementAudiencePreviewDto } from "../models/announcement-audience-preview-dto";

export function toAnnouncementAudiencePreview(
  dto: AnnouncementAudiencePreviewDto,
  expected: Announcement,
): AnnouncementAudiencePreview {
  const references = Object.entries(dto.audienceVersions)
    .map(([id, version]) => Object.freeze({ id: id.toLowerCase(), version }))
    .sort((a, b) => a.id.localeCompare(b.id));
  const ids = new Set(references.map((item) => item.id));
  if (
    dto.announcementId.toLowerCase() !== expected.id ||
    dto.version !== expected.version ||
    ids.size !== references.length ||
    ids.size !== expected.targetIds.length ||
    expected.targetIds.some((id) => !ids.has(id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    announcementId: expected.id,
    version: dto.version,
    asOfDate: dto.asOfDate,
    evaluatedAt: dto.evaluatedAt,
    recipientCount: dto.recipientCount,
    references: Object.freeze(references),
  });
}
