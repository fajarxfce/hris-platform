import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { DeviceSessionId } from "../../domain/entities/device-session";
import type { DeviceSessionRepository } from "../../domain/repositories/device-session-repository";
import type { DeviceSessionDataSource } from "../datasources/device-session-data-source";
import { toDeviceSessions } from "../mappers/device-session-mapper";

export class RemoteDeviceSessionRepository implements DeviceSessionRepository {
  constructor(private readonly source: DeviceSessionDataSource) {}
  list(signal: AbortSignal) {
    return safeHttpCall(signal, async () => toDeviceSessions(await this.source.list(signal)));
  }
  revoke(id: DeviceSessionId, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      await this.source.revoke(id, signal);
      return null;
    });
  }
}
