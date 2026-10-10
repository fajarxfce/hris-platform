import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../entities/session";
import { canManageCompanyMembers } from "../policies/company-member-policy";
import type { CompanyMemberRepository } from "../repositories/company-member-repository";

export class LoadCompanyMembers {
  constructor(private readonly members: CompanyMemberRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    if (!canManageCompanyMembers(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId) || (after !== null && !isUuid(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.members.list(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
