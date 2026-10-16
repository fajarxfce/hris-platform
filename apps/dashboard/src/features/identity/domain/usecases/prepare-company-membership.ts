import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyMembershipCandidate } from "../entities/company-membership-candidate";
import type { PlatformAccess } from "../entities/platform-access";
import type { CompanyAccess } from "../entities/session";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import { canManageCompanyMembers } from "../policies/company-member-policy";
import type { CompanyMemberRepository } from "../repositories/company-member-repository";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";
import type { RoleTemplateRepository } from "../repositories/role-template-repository";

/** A review is not a reservation; creation still requires an absent membership at commit. */
export class PrepareCompanyMembership {
  constructor(
    private readonly members: CompanyMemberRepository,
    private readonly accounts: ManagedAccountRepository,
    private readonly roles: RoleTemplateRepository,
  ) {}

  async execute(
    access: CompanyAccess,
    platform: PlatformAccess,
    accountId: string,
    signal: AbortSignal,
  ): Promise<Result<CompanyMembershipCandidate>> {
    signal.throwIfAborted();
    if (!canManageCompanyMembers(access.permissions)) return failed("access_denied");
    if (!canManagePlatformAccounts(platform)) return failed("platform_administrator_required");
    if (!isUuid(access.companyId)) return failed("company_required");
    if (!isUuid(accountId)) return failed("invalid_identifier");
    const id = accountId.toLowerCase() as AccountId;
    const existing = await this.members.get(access.companyId, id, signal);
    signal.throwIfAborted();
    if (existing.ok) return failed("company_member_already_exists");
    if (existing.failure.code !== "company_member_not_found") return existing;
    const account = await this.accounts.get(id, signal);
    signal.throwIfAborted();
    if (!account.ok) return account;
    if (!account.value.account.active || account.value.account.invitationPending)
      return failed("account_unavailable");
    const permissions = await this.roles.permissions(access.companyId, signal);
    signal.throwIfAborted();
    if (!permissions.ok) return permissions;
    return success(
      Object.freeze({
        accountId: id,
        displayName: account.value.account.displayName,
        email: account.value.account.email,
        assignablePermissions: Object.freeze([...permissions.value]),
      }),
    );
  }
}
