import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceQuery } from "../entities/leave-balance-query";
import { isLeaveBalanceYear } from "../policies/leave-balance-read-policy";
import { canBrowseEmployeeLeave } from "../policies/leave-read-policy";
import type { LeaveBalanceRepository } from "../repositories/leave-balance-repository";

export class LoadLeaveLedger {
  constructor(private readonly balances: LeaveBalanceRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    type: string,
    query: LeaveBalanceQuery,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canBrowseEmployeeLeave(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    if (!isUuid(type)) return Promise.resolve(failed("leave_type_not_found"));
    if (!isLeaveBalanceYear(query.year) || (query.after !== null && !isUuid(query.after)))
      return Promise.resolve(failed("invalid_page"));
    return this.balances.ledger(
      access.companyId,
      employee.toLowerCase(),
      type.toLowerCase(),
      Number(query.year),
      query.after?.toLowerCase() ?? null,
      signal,
    );
  }
}
