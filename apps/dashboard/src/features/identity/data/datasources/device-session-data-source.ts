import type { DeviceSessionDto } from "../models/device-session-dto";

export interface DeviceSessionDataSource {
  list(signal: AbortSignal): Promise<readonly DeviceSessionDto[]>;
  revoke(id: string, signal: AbortSignal): Promise<void>;
}
