import type { HttpClient } from "../../../../core/data/http/http-client";
import type { ApprovalReassignmentDto } from "../models/approval-reassignment-dto";
import { approvalInboxDto, approvalRequestDto } from "../models/approval-request-dto";
import { approvalAdministrationReceiptDto } from "../models/approval-template-dto";
import type { ApprovalDataSource } from "./approval-data-source";

export class HttpApprovalDataSource implements ApprovalDataSource {
  constructor(private readonly http: HttpClient) {}
  async inbox(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return approvalInboxDto.parse(
      await this.http.request({ path: `/api/v1/companies/${company}/approvals?${query}` }, signal),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return approvalRequestDto.parse(
      await this.http.request({ path: `/api/v1/companies/${company}/approvals/${id}` }, signal),
    );
  }
  async reassign(
    company: string,
    id: string,
    operation: string,
    body: ApprovalReassignmentDto,
    signal: AbortSignal,
  ) {
    return approvalAdministrationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/approvals/${id}/reassign`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
