import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AnnouncementId } from "../entities/announcement";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AnnouncementDeliveryRepository } from "../repositories/announcement-delivery-repository";

export class LoadAnnouncementDelivery {
  constructor(private readonly announcements: AnnouncementDeliveryRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("announcement_not_found"));
    return this.announcements.get(access.companyId, id.toLowerCase() as AnnouncementId, signal);
  }
}
