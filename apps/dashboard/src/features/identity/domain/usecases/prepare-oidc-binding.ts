import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { OidcBindingId } from "../entities/oidc-binding";
import type { OidcBindingReview } from "../entities/oidc-binding-review";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";
import type { OidcBindingRepository } from "../repositories/oidc-binding-repository";

export class PrepareOidcBinding {
  constructor(
    private readonly accounts: ManagedAccountRepository,
    private readonly bindings: OidcBindingRepository,
  ) {}
  async execute(
    access: PlatformAccess,
    accountId: string,
    bindingId: string | null,
    signal: AbortSignal,
  ): Promise<Result<OidcBindingReview>> {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access)) return failed("platform_administrator_required");
    if (!isUuid(accountId) || (bindingId !== null && !isUuid(bindingId)))
      return failed("invalid_identifier");
    const id = accountId.toLowerCase() as AccountId;
    const account = await this.accounts.get(id, signal);
    signal.throwIfAborted();
    if (!account.ok) return account;
    if (bindingId === null)
      return account.value.account.active
        ? success(Object.freeze({ account: account.value.account, binding: null }))
        : failed("account_disabled");
    const binding = await this.bindings.get(id, bindingId.toLowerCase() as OidcBindingId, signal);
    signal.throwIfAborted();
    return binding.ok
      ? success(Object.freeze({ account: account.value.account, binding: binding.value }))
      : binding;
  }
}
