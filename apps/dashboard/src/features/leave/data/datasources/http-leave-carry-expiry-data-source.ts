import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type LeaveCarryExpiryResumeDto,
  leaveCarryExpiryDetailsDto,
  leaveCarryExpiryPageDto,
} from "../models/leave-carry-expiry-dto";
import { leaveReceiptDto } from "../models/leave-receipt-dto";
import type { LeaveCarryExpiryDataSource } from "./leave-carry-expiry-data-source";
export class HttpLeaveCarryExpiryDataSource implements LeaveCarryExpiryDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveCarryExpiryPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/carry-expiries?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveCarryExpiryDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/carry-expiries/${id}?${query}` },
        signal,
      ),
    );
  }
  async resume(
    company: string,
    id: string,
    operation: string,
    input: LeaveCarryExpiryResumeDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/carry-expiries/${id}/resume`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
