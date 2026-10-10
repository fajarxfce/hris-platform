import type { LeaveBalanceAdjustmentDto } from "../models/leave-balance-adjustment-dto";
import type { EmployeeLeaveBalancesDto } from "../models/leave-balance-dto";
import type { LeaveLedgerDto } from "../models/leave-ledger-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";

export interface LeaveBalanceDataSource {
  adjust(
    company: string,
    operation: string,
    employee: string,
    type: string,
    year: number,
    body: LeaveBalanceAdjustmentDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
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
