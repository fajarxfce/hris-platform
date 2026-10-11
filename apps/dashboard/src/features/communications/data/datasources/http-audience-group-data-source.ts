import type { HttpClient } from "../../../../core/data/http/http-client";
import type { AudienceGroupChangeDto } from "../models/audience-group-change-dto";
import { audienceGroupDto, audienceGroupPageDto } from "../models/audience-group-dto";
import { communicationsReceiptDto } from "../models/communications-receipt-dto";
import type { AudienceGroupDataSource } from "./audience-group-data-source";

export class HttpAudienceGroupDataSource implements AudienceGroupDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "50" });
    if (after !== null) query.set("after", after);
    return audienceGroupPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/communications/audience-groups?${query}`,
        },
        signal,
      ),
    );
  }
  async get(company: string, id: string, revision: number | null, signal: AbortSignal) {
    return audienceGroupDto.parse(
      await this.http.request(
        {
          path:
            "/api/v1/companies/" +
            company +
            "/communications/audience-groups/" +
            id +
            (revision === null ? "" : `/revisions/${revision}`),
        },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    input: AudienceGroupChangeDto,
    signal: AbortSignal,
  ) {
    return communicationsReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/communications/audience-groups/${id}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
