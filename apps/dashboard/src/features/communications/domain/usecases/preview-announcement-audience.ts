import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { Announcement } from "../entities/announcement";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AnnouncementRepository } from "../repositories/announcement-repository";

export class PreviewAnnouncementAudience {
  constructor(private readonly announcements: AnnouncementRepository) {}
  execute(access: CompanyAccess, announcement: Announcement, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions) || announcement.companyId !== access.companyId)
      return Promise.resolve(failed("access_denied"));
    if (!["DRAFT", "QUEUED"].includes(announcement.status))
      return Promise.resolve(failed("announcement_preview_unavailable"));
    return this.announcements.preview(access.companyId, announcement, signal);
  }
}
