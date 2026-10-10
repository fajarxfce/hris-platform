import type { HttpClient } from "../../../../core/data/http/http-client";
import { leaveRequestDetailsDto } from "../models/leave-request-details-dto";
import { leaveRequestPageDto } from "../models/leave-request-summary-dto";
import type { LeaveRequestDataSource } from "./leave-request-data-source";

export class HttpLeaveRequestDataSource implements LeaveRequestDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(
    company: string,
    employee: string | null,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ limit: "20" });
    if (employee !== null) query.set("employeeId", employee);
    if (status !== null) query.set("status", status);
    if (after !== null) query.set("after", after);
    return leaveRequestPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/requests?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, historyAfter: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20" });
    if (historyAfter !== null) query.set("historyAfter", historyAfter);
    return leaveRequestDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/requests/${id}?${query}` },
        signal,
      ),
    );
  }
}
