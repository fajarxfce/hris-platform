import type { HttpClient } from "../../../../core/data/http/http-client";
import { auditPageDto } from "../models/audit-page-dto";
import type { AuditSearchDto } from "../models/audit-search-dto";
import type { AuditDataSource } from "./audit-data-source";

export class HttpAuditDataSource implements AuditDataSource {
  constructor(private readonly http: HttpClient) {}

  async search(companyId: string, query: AuditSearchDto, signal: AbortSignal) {
    const parameters = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== null) parameters.set(key, String(value));
    }
    return auditPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/audit-events?${parameters}` },
        signal,
      ),
    );
  }
}
