import type { Result } from "../../../../core/domain/result";
import type { DeviceSession, DeviceSessionId } from "../entities/device-session";

export interface DeviceSessionRepository {
  list(signal: AbortSignal): Promise<Result<readonly DeviceSession[]>>;
  revoke(id: DeviceSessionId, signal: AbortSignal): Promise<Result<null>>;
}
