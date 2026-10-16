import { isUuid } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { DeviceSessionId } from "../entities/device-session";
import type { DeviceSessionRepository } from "../repositories/device-session-repository";

export class RevokeDeviceSession {
  constructor(private readonly sessions: DeviceSessionRepository) {}
  async execute(id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!isUuid(id)) return failed("invalid_identifier");
    const result = await this.sessions.revoke(id.toLowerCase() as DeviceSessionId, signal);
    signal.throwIfAborted();
    // Ending an already removed session has the same outcome as its revocation.
    return !result.ok && result.failure.code === "native_session_not_found"
      ? success(null)
      : result;
  }
}
