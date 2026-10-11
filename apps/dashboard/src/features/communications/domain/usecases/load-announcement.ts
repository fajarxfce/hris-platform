import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AnnouncementId } from "../entities/announcement";
import {
  canManageAnnouncements,
  parseAnnouncementVersion,
} from "../policies/announcement-read-policy";
import type { AnnouncementRepository } from "../repositories/announcement-repository";

export class LoadAnnouncement {
  constructor(private readonly announcements: AnnouncementRepository) {}
  execute(access: CompanyAccess, id: string, version: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("announcement_not_found"));
    const parsed = parseAnnouncementVersion(version);
    if (!parsed.ok) return Promise.resolve(parsed);
    return this.announcements.get(
      access.companyId,
      id.toLowerCase() as AnnouncementId,
      parsed.value,
      signal,
    );
  }
}
