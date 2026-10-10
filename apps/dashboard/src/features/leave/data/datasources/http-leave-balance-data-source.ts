import type { HttpClient } from "../../../../core/data/http/http-client";
import { employeeLeaveBalancesDto } from "../models/leave-balance-dto";
import { leaveLedgerDto } from "../models/leave-ledger-dto";
import type { LeaveBalanceDataSource } from "./leave-balance-data-source";

export class HttpLeaveBalanceDataSource implements LeaveBalanceDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(
    company: string,
    employee: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ year: String(year), limit: "20" });
    if (after !== null) query.set("after", after);
    return employeeLeaveBalancesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/employees/${employee}/balances?${query}` },
        signal,
      ),
    );
  }
  async ledger(
    company: string,
    employee: string,
    type: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveLedgerDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/employees/${employee}/balances/${type}/${year}?${query}`,
        },
        signal,
      ),
    );
  }
}
