import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type OrganizationChangeDto,
  organizationReceiptDto,
} from "../models/organization-change-dto";
import type { OrganizationSearchDto } from "../models/organization-search-dto";
import {
  organizationUnitDetailsDto,
  organizationUnitPageDto,
} from "../models/organization-unit-dto";
import type { OrganizationDataSource } from "./organization-data-source";

export class HttpOrganizationDataSource implements OrganizationDataSource {
  constructor(private readonly http: HttpClient) {}
  async save(
    company: string,
    id: string,
    operation: string,
    change: OrganizationChangeDto,
    signal: AbortSignal,
  ) {
    return organizationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/organization-units/${id}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async list(company: string, search: OrganizationSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ query: search.query, limit: "50" });
    if (search.kind !== null) query.set("kind", search.kind);
    if (search.active !== null) query.set("active", String(search.active));
    if (search.after !== null) query.set("after", search.after);
    return organizationUnitPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/organization-units?${query}`,
        },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return organizationUnitDetailsDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/organization-units/${id}`,
        },
        signal,
      ),
    );
  }
}
