import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { DeviceSessionId } from "../../domain/entities/device-session";
import type { AccountDeviceSessionRepository } from "../../domain/repositories/account-device-session-repository";
import type { AccountDeviceSessionDataSource } from "../datasources/account-device-session-data-source";
import { toDeviceSessions } from "../mappers/device-session-mapper";

export class RemoteAccountDeviceSessionRepository implements AccountDeviceSessionRepository {
  constructor(private readonly source: AccountDeviceSessionDataSource) {}
  list(accountId: AccountId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDeviceSessions(await this.source.list(accountId, signal)),
    );
  }
  revoke(accountId: AccountId, id: DeviceSessionId, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      await this.source.revoke(accountId, id, signal);
      return null;
    });
  }
}
