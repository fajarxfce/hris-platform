import type { HttpClient } from "../../../../core/data/http/http-client";
import type { RoleTemplateChangeDto } from "../models/role-template-change-dto";
import {
  rolePermissionsDto,
  roleTemplatePageDto,
  roleTemplateReceiptDto,
  roleTemplateReviewDto,
} from "../models/role-template-dto";
import type { RoleTemplateDataSource } from "./role-template-data-source";
export class HttpRoleTemplateDataSource implements RoleTemplateDataSource {
  constructor(private readonly http: HttpClient) {}
  async permissions(company: string, signal: AbortSignal) {
    return rolePermissionsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/role-templates/permissions` },
        signal,
      ),
    );
  }
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return roleTemplatePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/role-templates?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, historyAfter: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20" });
    if (historyAfter !== null) query.set("historyAfter", historyAfter);
    return roleTemplateReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/role-templates/${id}?${query}` },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    input: RoleTemplateChangeDto,
    signal: AbortSignal,
  ) {
    return roleTemplateReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/role-templates/${id}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
