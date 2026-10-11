import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type AudienceReferenceQueryDto,
  audienceReferencePageDto,
} from "../models/audience-reference-dto";
import type { AudienceReferenceDataSource } from "./audience-reference-data-source";

export class HttpAudienceReferenceDataSource implements AudienceReferenceDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, search: AudienceReferenceQueryDto, signal: AbortSignal) {
    const query = new URLSearchParams({ kind: search.kind, limit: "50" });
    if (search.query) query.set("query", search.query);
    if (search.after !== null) query.set("after", search.after);
    for (const id of search.ids) query.append("ids", id);
    return audienceReferencePageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/communications/audience-references?${query}`,
        },
        signal,
      ),
    );
  }
}
