import type { HttpClient } from "../../../../core/data/http/http-client";
import { deviceSessionsDto } from "../models/device-session-dto";
import type { AccountDeviceSessionDataSource } from "./account-device-session-data-source";

export class HttpAccountDeviceSessionDataSource implements AccountDeviceSessionDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(accountId: string, signal: AbortSignal) {
    return deviceSessionsDto.parse(
      await this.http.request(
        {
          path: `/api/v1/identity/accounts/${encodeURIComponent(accountId)}/native-sessions`,
        },
        signal,
      ),
    );
  }
  async revoke(accountId: string, id: string, signal: AbortSignal): Promise<void> {
    await this.http.request(
      {
        path: `/api/v1/identity/accounts/${encodeURIComponent(accountId)}/native-sessions/${encodeURIComponent(id)}`,
        method: "DELETE",
        response: { type: "empty", status: 204 },
      },
      signal,
    );
  }
}
