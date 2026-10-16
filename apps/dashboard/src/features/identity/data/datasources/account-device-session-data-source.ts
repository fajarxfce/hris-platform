import type { DeviceSessionDto } from "../models/device-session-dto";

export interface AccountDeviceSessionDataSource {
  list(accountId: string, signal: AbortSignal): Promise<readonly DeviceSessionDto[]>;
  revoke(accountId: string, id: string, signal: AbortSignal): Promise<void>;
}
