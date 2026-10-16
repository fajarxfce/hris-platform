import type { HttpClient } from "../../../../core/data/http/http-client";
import { deviceSessionsDto } from "../models/device-session-dto";
import type { DeviceSessionDataSource } from "./device-session-data-source";

export class HttpDeviceSessionDataSource implements DeviceSessionDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(signal: AbortSignal) {
    return deviceSessionsDto.parse(
      await this.http.request({ path: "/api/v1/auth/native/sessions" }, signal),
    );
  }
  async revoke(id: string, signal: AbortSignal): Promise<void> {
    await this.http.request(
      {
        path: `/api/v1/auth/native/sessions/${id}`,
        method: "DELETE",
        response: { type: "empty", status: 204 },
      },
      signal,
    );
  }
}
