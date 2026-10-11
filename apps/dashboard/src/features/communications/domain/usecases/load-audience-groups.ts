import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AudienceGroupRepository } from "../repositories/audience-group-repository";

export class LoadAudienceGroups {
  constructor(private readonly groups: AudienceGroupRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.groups.list(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
