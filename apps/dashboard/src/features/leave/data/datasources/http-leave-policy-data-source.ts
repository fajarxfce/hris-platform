import type { HttpClient } from "../../../../core/data/http/http-client";
import type { LeavePolicyChangeDto } from "../models/leave-policy-change-dto";
import { leaveReceiptDto } from "../models/leave-receipt-dto";
import { leavePolicyReviewDto, leaveTypePageDto } from "../models/leave-type-dto";
import type { LeavePolicyDataSource } from "./leave-policy-data-source";

export class HttpLeavePolicyDataSource implements LeavePolicyDataSource {
  constructor(private readonly http: HttpClient) {}
  async save(
    company: string,
    id: string,
    operation: string,
    change: LeavePolicyChangeDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/types/${id}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async list(company: string, active: boolean | null, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (active !== null) query.set("active", String(active));
    if (after !== null) query.set("after", after);
    return leaveTypePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/policies?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, historyAfter: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20" });
    if (historyAfter !== null) query.set("historyAfter", historyAfter);
    return leavePolicyReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/policies/${id}?${query}` },
        signal,
      ),
    );
  }
}
