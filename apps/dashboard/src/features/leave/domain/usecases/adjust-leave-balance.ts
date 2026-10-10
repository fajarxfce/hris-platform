import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceAdjustment } from "../entities/leave-balance-adjustment";
import {
  canManageLeaveBalances,
  normalizeLeaveBalanceAdjustment,
  validateLeaveBalanceAdjustment,
} from "../policies/leave-balance-adjustment-policy";
import type { LeaveBalanceRepository } from "../repositories/leave-balance-repository";

export class AdjustLeaveBalance {
  constructor(private readonly balances: LeaveBalanceRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LeaveBalanceAdjustment,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageLeaveBalances(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_leave_adjustment"));
    const change = normalizeLeaveBalanceAdjustment(input);
    const failure = validateLeaveBalanceAdjustment(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.balances.adjust(access.companyId, operation, change, signal);
  }
}
