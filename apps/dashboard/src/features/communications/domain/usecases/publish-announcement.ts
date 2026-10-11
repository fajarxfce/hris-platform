import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AnnouncementCommandInput } from "../entities/announcement-command";
import {
  normalizeAnnouncementCommand,
  validateAnnouncementCommand,
} from "../policies/announcement-command-policy";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AnnouncementRepository } from "../repositories/announcement-repository";

export class PublishAnnouncement {
  constructor(private readonly announcements: AnnouncementRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: AnnouncementCommandInput & Readonly<{ scheduledFor: string | null }>,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_announcement_command"));
    const command = normalizeAnnouncementCommand({ ...input, action: "PUBLISH" });
    const invalid = validateAnnouncementCommand(command);
    if (invalid) return Promise.resolve({ ok: false as const, failure: invalid });
    return this.announcements.command(access.companyId, operation, command, signal);
  }
}
