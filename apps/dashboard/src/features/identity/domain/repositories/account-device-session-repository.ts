import type { AccountId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { DeviceSession, DeviceSessionId } from "../entities/device-session";

export interface AccountDeviceSessionRepository {
  list(accountId: AccountId, signal: AbortSignal): Promise<Result<readonly DeviceSession[]>>;
  revoke(accountId: AccountId, id: DeviceSessionId, signal: AbortSignal): Promise<Result<null>>;
}
