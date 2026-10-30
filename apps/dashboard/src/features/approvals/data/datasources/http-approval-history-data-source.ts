import type { HttpClient } from "../../../../core/data/http/http-client";
import { approvalHistoryDto } from "../models/approval-history-dto";
import type { ApprovalHistoryDataSource } from "./approval-history-data-source";
export class HttpApprovalHistoryDataSource implements ApprovalHistoryDataSource {
  constructor(private readonly http: HttpClient) {}
  async get(
    company: string,
    id: string,
    after: number | null,
    expectedVersion: number | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ limit: "50" });
    if (after !== null) query.set("after", String(after));
    if (expectedVersion !== null) query.set("expectedVersion", String(expectedVersion));
    return approvalHistoryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/approvals/${id}/history?${query}` },
        signal,
      ),
    );
  }
}
