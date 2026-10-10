import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAdjustmentCatalog } from "../entities/leave-adjustment-catalog";
import type { LeaveBalanceQuery } from "../entities/leave-balance-query";
import { canManageLeaveBalances } from "../policies/leave-balance-adjustment-policy";
import { isLeaveBalanceYear } from "../policies/leave-balance-read-policy";
import { canBrowseEmployeeLeave } from "../policies/leave-read-policy";
import type { LeaveBalanceRepository } from "../repositories/leave-balance-repository";
import type { LeavePolicyRepository } from "../repositories/leave-policy-repository";

export class LoadLeaveAdjustmentCatalog {
  constructor(
    private readonly balances: LeaveBalanceRepository,
    private readonly policies: LeavePolicyRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    query: LeaveBalanceQuery,
    signal: AbortSignal,
  ): Promise<Result<LeaveAdjustmentCatalog>> {
    signal.throwIfAborted();
    if (!canManageLeaveBalances(access.permissions) || !canBrowseEmployeeLeave(access.permissions))
      return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    if (
      !isLeaveBalanceYear(query.year) ||
      (query.after !== null && !/^[A-Z][A-Z0-9_-]{0,31}$/u.test(query.after))
    )
      return failed("invalid_page");
    const balance = await this.balances.list(
      access.companyId,
      employee.toLowerCase(),
      Number(query.year),
      null,
      signal,
    );
    signal.throwIfAborted();
    if (!balance.ok) return balance;
    const policies = await this.policies.list(access.companyId, null, query.after, signal);
    signal.throwIfAborted();
    if (!policies.ok) return policies;
    return success(
      Object.freeze({
        companyId: access.companyId,
        employee: balance.value.employee,
        year: balance.value.year,
        policies: policies.value,
      }),
    );
  }
}
