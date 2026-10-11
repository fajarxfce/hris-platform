import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementReview } from "../../domain/entities/announcement-review";
import type { AnnouncementReviewDto } from "../models/announcement-review-dto";
import { toAnnouncement } from "./announcement-mapper";

export function toAnnouncementReview(
  dto: AnnouncementReviewDto,
  company: CompanyId,
  id: AnnouncementId,
): AnnouncementReview {
  const announcement = toAnnouncement(dto.announcement, company, id, null);
  const job = dto.publicationJob
    ? Object.freeze({ ...dto.publicationJob, id: dto.publicationJob.id.toLowerCase() })
    : null;
  if (
    announcement.version > 999 ||
    announcement.publicationJobId !== (job?.id ?? null) ||
    new Set(dto.availableActions).size !== dto.availableActions.length ||
    (dto.availableActions.includes("VIEW_JOB") && job === null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    announcement,
    publicationJob: job,
    availableActions: Object.freeze([...dto.availableActions]),
    evaluatedAt: dto.evaluatedAt,
  });
}
