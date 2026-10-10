import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type ApprovalDelegationChangeDto,
  approvalDelegationDto,
  approvalDelegationPageDto,
} from "../models/approval-delegation-dto";
import { approvalAdministrationReceiptDto } from "../models/approval-template-dto";
import type { ApprovalDelegationDataSource } from "./approval-delegation-data-source";

export class HttpApprovalDelegationDataSource implements ApprovalDelegationDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return approvalDelegationPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/approvals/delegations?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return approvalDelegationDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/approvals/delegations/${id}` },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    change: ApprovalDelegationChangeDto,
    signal: AbortSignal,
  ) {
    return approvalAdministrationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/approvals/delegations/${id}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
}
