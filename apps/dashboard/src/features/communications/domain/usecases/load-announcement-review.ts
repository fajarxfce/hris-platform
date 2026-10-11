import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AnnouncementId } from "../entities/announcement";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AnnouncementRepository } from "../repositories/announcement-repository";

export class LoadAnnouncementReview {
  constructor(private readonly announcements: AnnouncementRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("announcement_not_found"));
    return this.announcements.review(access.companyId, id.toLowerCase() as AnnouncementId, signal);
  }
}
