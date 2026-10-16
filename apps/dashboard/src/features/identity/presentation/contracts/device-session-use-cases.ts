import type { LoadDeviceSessions } from "../../domain/usecases/load-device-sessions";
import type { RevokeDeviceSession } from "../../domain/usecases/revoke-device-session";

export type DeviceSessionUseCases = Readonly<{
  loadDeviceSessions: Pick<LoadDeviceSessions, "execute">;
  revokeDeviceSession: Pick<RevokeDeviceSession, "execute">;
}>;
