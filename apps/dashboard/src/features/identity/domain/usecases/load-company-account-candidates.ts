import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccountAccess } from "../entities/company-account-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import { canManageCompanyMembers } from "../policies/company-member-policy";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";

export class LoadCompanyAccountCandidates {
  constructor(private readonly accounts: ManagedAccountRepository) {}
  execute(access: CompanyAccountAccess, query: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageCompanyMembers(access.company.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!canManagePlatformAccounts(access.platform))
      return Promise.resolve(failed("platform_administrator_required"));
    if (!isUuid(access.company.companyId)) return Promise.resolve(failed("company_required"));
    if (query.length > 100 || (after !== null && !isUuid(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.accounts.list(query.trim().toLowerCase(), after?.toLowerCase() ?? null, signal);
  }
}
