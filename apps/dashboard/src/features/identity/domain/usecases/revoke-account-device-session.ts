import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { DeviceSessionId } from "../entities/device-session";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import type { AccountDeviceSessionRepository } from "../repositories/account-device-session-repository";

export class RevokeAccountDeviceSession {
  constructor(private readonly sessions: AccountDeviceSessionRepository) {}
  async execute(access: PlatformAccess, accountId: string, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access)) return failed("platform_administrator_required");
    if (!isUuid(accountId) || !isUuid(id)) return failed("invalid_identifier");
    const result = await this.sessions.revoke(
      accountId.toLowerCase() as AccountId,
      id.toLowerCase() as DeviceSessionId,
      signal,
    );
    signal.throwIfAborted();
    // Absence in the selected account completes the same session-ending intent.
    return !result.ok && result.failure.code === "native_session_not_found"
      ? success(null)
      : result;
  }
}
