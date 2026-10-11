import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AudienceReferenceSearch } from "../entities/audience-reference-search";
import { canManageAnnouncements } from "../policies/announcement-read-policy";
import type { AudienceReferenceRepository } from "../repositories/audience-reference-repository";

export class LoadAudienceReferences {
  constructor(private readonly references: AudienceReferenceRepository) {}
  execute(access: CompanyAccess, input: AudienceReferenceSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageAnnouncements(access.permissions))
      return Promise.resolve(failed("access_denied"));
    const search: AudienceReferenceSearch = Object.freeze({
      ...input,
      query: input.query.trim(),
      ids: Object.freeze(input.ids.map((id) => id.toLowerCase()).sort()),
      after: input.after?.toLowerCase() ?? null,
    });
    if (
      !["BRANCH", "DEPARTMENT", "GROUP", "EMPLOYMENT"].includes(search.kind) ||
      search.query.length > 120 ||
      /\p{Cc}/u.test(search.query) ||
      (search.after !== null && !isUuid(search.after)) ||
      search.ids.length > 50 ||
      search.ids.some((id) => !isUuid(id)) ||
      new Set(search.ids).size !== search.ids.length ||
      (search.ids.length > 0 && (search.query.length > 0 || search.after !== null))
    )
      return Promise.resolve(failed("invalid_audience_reference_search"));
    return this.references.list(access.companyId, search, signal);
  }
}
