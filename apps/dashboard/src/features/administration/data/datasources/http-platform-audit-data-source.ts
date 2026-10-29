import type { HttpClient } from "../../../../core/data/http/http-client";
import type { AuditSearchDto } from "../models/audit-search-dto";
import { platformAuditPageDto } from "../models/platform-audit-page-dto";
import type { PlatformAuditDataSource } from "./platform-audit-data-source";

export class HttpPlatformAuditDataSource implements PlatformAuditDataSource {
  constructor(private readonly http: HttpClient) {}

  async search(query: AuditSearchDto, signal: AbortSignal) {
    const parameters = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== null) parameters.set(key, String(value));
    }
    return platformAuditPageDto.parse(
      await this.http.request(
        { path: `/api/v1/administration/audit-events?${parameters}` },
        signal,
      ),
    );
  }
}
