import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  canAdjustLeaveBalance,
  canManageLeaveBalances,
} from "../policies/leave-balance-adjustment-policy";
import { isLeaveBalanceYear } from "../policies/leave-balance-read-policy";
import { canBrowseEmployeeLeave } from "../policies/leave-read-policy";
import type { LeaveBalanceRepository } from "../repositories/leave-balance-repository";

export class ReviewLeaveBalanceAdjustment {
  constructor(private readonly balances: LeaveBalanceRepository) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    type: string,
    year: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageLeaveBalances(access.permissions) || !canBrowseEmployeeLeave(access.permissions))
      return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    if (!isUuid(type)) return failed("leave_type_not_found");
    if (!isLeaveBalanceYear(year)) return failed("invalid_page");
    const result = await this.balances.ledger(
      access.companyId,
      employee.toLowerCase(),
      type.toLowerCase(),
      Number(year),
      null,
      signal,
    );
    signal.throwIfAborted();
    if (result.ok && !canAdjustLeaveBalance(access, result.value))
      return failed("leave_adjustment_unavailable");
    return result;
  }
}
