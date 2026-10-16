import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { OidcBindingDirectory } from "../entities/oidc-binding-directory";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";
import type { OidcBindingRepository } from "../repositories/oidc-binding-repository";

export class LoadOidcBindings {
  constructor(
    private readonly accounts: ManagedAccountRepository,
    private readonly bindings: OidcBindingRepository,
  ) {}
  async execute(
    access: PlatformAccess,
    accountId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<OidcBindingDirectory>> {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access)) return failed("platform_administrator_required");
    if (!isUuid(accountId)) return failed("invalid_identifier");
    if (after !== null && !isUuid(after)) return failed("invalid_page");
    const id = accountId.toLowerCase() as AccountId;
    const account = await this.accounts.get(id, signal);
    signal.throwIfAborted();
    if (!account.ok) return account;
    const page = await this.bindings.list(id, after?.toLowerCase() ?? null, signal);
    signal.throwIfAborted();
    return page.ok
      ? success(Object.freeze({ account: account.value.account, page: page.value }))
      : page;
  }
}
