import type { AccountId } from "../../../../core/domain/identifiers";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../entities/session";
import { canManageCompanyMembers } from "../policies/company-member-policy";
import type { CompanyMemberRepository } from "../repositories/company-member-repository";

export class LoadCompanyMember {
  constructor(private readonly members: CompanyMemberRepository) {}
  execute(access: CompanyAccess, accountId: string, signal: AbortSignal) {
    if (!canManageCompanyMembers(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId) || !isUuid(accountId))
      return Promise.resolve(failed("company_member_not_found"));
    return this.members.get(access.companyId, accountId.toLowerCase() as AccountId, signal);
  }
}
