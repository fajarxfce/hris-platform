import type { DeviceSessionRepository } from "../repositories/device-session-repository";

export class LoadDeviceSessions {
  constructor(private readonly sessions: DeviceSessionRepository) {}
  execute(signal: AbortSignal) {
    signal.throwIfAborted();
    return this.sessions.list(signal);
  }
}
