import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AudienceGroupChange } from "../entities/audience-group-change";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import {
  normalizeAudienceGroupChange,
  validateAudienceGroupChange,
} from "../policies/audience-group-policy";
import type { AudienceGroupRepository } from "../repositories/audience-group-repository";

export class SaveAudienceGroup {
  constructor(private readonly groups: AudienceGroupRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: AudienceGroupChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_audience_group"));
    const normalized = normalizeAudienceGroupChange(input);
    const invalid = validateAudienceGroupChange(normalized);
    if (invalid) return Promise.resolve({ ok: false as const, failure: invalid });
    return this.groups.save(access.companyId, operation, normalized, signal);
  }
}
