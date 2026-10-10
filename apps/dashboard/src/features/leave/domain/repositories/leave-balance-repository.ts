import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeLeaveBalances } from "../entities/employee-leave-balances";
import type { LeaveBalanceAdjustment } from "../entities/leave-balance-adjustment";
import type { LeaveLedger } from "../entities/leave-ledger";

export interface LeaveBalanceRepository {
  adjust(
    company: CompanyId,
    operation: OperationId,
    change: LeaveBalanceAdjustment,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
