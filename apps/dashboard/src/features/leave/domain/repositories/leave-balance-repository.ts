import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeLeaveBalances } from "../entities/employee-leave-balances";
import type { LeaveLedger } from "../entities/leave-ledger";

export interface LeaveBalanceRepository {
  list(
    company: CompanyId,
    employee: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<EmployeeLeaveBalances>>;
  ledger(
    company: CompanyId,
    employee: string,
    type: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveLedger>>;
}
