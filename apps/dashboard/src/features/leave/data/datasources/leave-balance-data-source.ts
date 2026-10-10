import type { EmployeeLeaveBalancesDto } from "../models/leave-balance-dto";
import type { LeaveLedgerDto } from "../models/leave-ledger-dto";

export interface LeaveBalanceDataSource {
  list(
    company: string,
    employee: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ): Promise<EmployeeLeaveBalancesDto>;
  ledger(
    company: string,
    employee: string,
    type: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveLedgerDto>;
}
