import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";
export class LoadManagedAccounts {
  constructor(private readonly accounts: ManagedAccountRepository) {}
  execute(access: PlatformAccess, query: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access))
      return Promise.resolve(failed("platform_administrator_required"));
    if (query.length > 100 || (after !== null && !isUuid(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.accounts.list(query.trim().toLowerCase(), after?.toLowerCase() ?? null, signal);
  }
}
