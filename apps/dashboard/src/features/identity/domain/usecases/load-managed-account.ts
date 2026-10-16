import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";
export class LoadManagedAccount {
  constructor(private readonly accounts: ManagedAccountRepository) {}
  execute(access: PlatformAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access))
      return Promise.resolve(failed("platform_administrator_required"));
    if (!isUuid(id)) return Promise.resolve(failed("invalid_identifier"));
    return this.accounts.get(id.toLowerCase() as AccountId, signal);
  }
}
