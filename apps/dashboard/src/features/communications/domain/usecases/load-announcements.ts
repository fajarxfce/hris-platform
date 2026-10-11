import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AnnouncementRepository } from "../repositories/announcement-repository";

export class LoadAnnouncements {
  constructor(private readonly announcements: AnnouncementRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.announcements.list(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
