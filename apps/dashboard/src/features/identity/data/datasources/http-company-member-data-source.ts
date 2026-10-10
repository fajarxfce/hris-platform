import type { HttpClient } from "../../../../core/data/http/http-client";
import { companyMemberGrantDto, companyMemberPageDto } from "../models/company-member-dto";
import type { CompanyMemberDataSource } from "./company-member-data-source";

export class HttpCompanyMemberDataSource implements CompanyMemberDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(companyId: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "50" });
    if (after !== null) query.set("after", after);
    return companyMemberPageDto.parse(
      await this.http.request({ path: `/api/v1/companies/${companyId}/members?${query}` }, signal),
    );
  }
  async get(companyId: string, accountId: string, signal: AbortSignal) {
    return companyMemberGrantDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/members/${accountId}` },
        signal,
      ),
    );
  }
}
