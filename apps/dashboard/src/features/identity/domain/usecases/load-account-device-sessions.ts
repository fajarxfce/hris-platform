import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { AccountDeviceSessionDirectory } from "../entities/account-device-session-directory";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import type { AccountDeviceSessionRepository } from "../repositories/account-device-session-repository";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";

export class LoadAccountDeviceSessions {
  constructor(
    private readonly accounts: ManagedAccountRepository,
    private readonly sessions: AccountDeviceSessionRepository,
  ) {}
  async execute(
    access: PlatformAccess,
    accountId: string,
    signal: AbortSignal,
  ): Promise<Result<AccountDeviceSessionDirectory>> {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access)) return failed("platform_administrator_required");
    if (!isUuid(accountId)) return failed("invalid_identifier");
    const id = accountId.toLowerCase() as AccountId;
    const account = await this.accounts.get(id, signal);
    signal.throwIfAborted();
    if (!account.ok) return account;
    const sessions = await this.sessions.list(id, signal);
    signal.throwIfAborted();
    return sessions.ok
      ? success(Object.freeze({ account: account.value.account, sessions: sessions.value }))
      : sessions;
  }
}
