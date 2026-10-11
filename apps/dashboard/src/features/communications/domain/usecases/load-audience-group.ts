import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AudienceGroupId } from "../entities/audience-group";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AudienceGroupRepository } from "../repositories/audience-group-repository";

export class LoadAudienceGroup {
  constructor(private readonly groups: AudienceGroupRepository) {}
  execute(access: CompanyAccess, id: string, revision: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("audience_group_not_found"));
    if (revision !== null && !/^(0|[1-9][0-9]{0,2})$/u.test(revision))
      return Promise.resolve(failed("invalid_revision"));
    return this.groups.get(
      access.companyId,
      id.toLowerCase() as AudienceGroupId,
      revision === null ? null : Number(revision),
      signal,
    );
  }
}
