import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AnnouncementChange } from "../entities/announcement-change";
import {
  normalizeAnnouncementChange,
  validateAnnouncementChange,
} from "../policies/announcement-change-policy";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AnnouncementRepository } from "../repositories/announcement-repository";

export class SaveAnnouncement {
  constructor(private readonly announcements: AnnouncementRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: AnnouncementChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_announcement"));
    const change = normalizeAnnouncementChange(input);
    const failure = validateAnnouncementChange(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.announcements.save(access.companyId, operation, change, signal);
  }
}
