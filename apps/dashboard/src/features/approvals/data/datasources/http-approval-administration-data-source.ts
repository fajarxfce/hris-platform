import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type ApprovalAssigneeSearchDto,
  approvalAssigneePageDto,
} from "../models/approval-assignee-dto";
import {
  type ApprovalTemplateChangeDto,
  type ApprovalTemplateSearchDto,
  approvalAdministrationReceiptDto,
  approvalTemplateDto,
  approvalTemplatePageDto,
} from "../models/approval-template-dto";
import type { ApprovalAdministrationDataSource } from "./approval-administration-data-source";

export class HttpApprovalAdministrationDataSource implements ApprovalAdministrationDataSource {
  constructor(private readonly http: HttpClient) {}
  async templates(company: string, search: ApprovalTemplateSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ kind: search.kind, asOf: search.asOf, limit: "20" });
    if (search.after !== null) query.set("after", search.after);
    return approvalTemplatePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/approvals/templates?${query}` },
        signal,
      ),
    );
  }
  async template(company: string, id: string, revision: number | null, signal: AbortSignal) {
    const query = revision === null ? "" : `?revision=${revision}`;
    return approvalTemplateDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/approvals/templates/${id}${query}` },
        signal,
      ),
    );
  }
  async saveTemplate(
    company: string,
    id: string,
    operation: string,
    change: ApprovalTemplateChangeDto,
    signal: AbortSignal,
  ) {
    return approvalAdministrationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/approvals/templates/${id}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async assignees(company: string, search: ApprovalAssigneeSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ kind: search.kind, query: search.query, limit: "10" });
    if (search.after !== null) query.set("after", search.after);
    return approvalAssigneePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/approvals/assignees?${query}` },
        signal,
      ),
    );
  }
}
