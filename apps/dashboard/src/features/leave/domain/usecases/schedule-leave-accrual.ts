import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAccrualScheduleCreation } from "../entities/leave-accrual-schedule-command";
import {
  canScheduleLeaveAccrual,
  normalizeLeaveAccrualScheduleCreation,
} from "../policies/leave-accrual-schedule-policy";
import type { LeaveAccrualScheduleRepository } from "../repositories/leave-accrual-schedule-repository";
export class ScheduleLeaveAccrual {
  constructor(private readonly schedules: LeaveAccrualScheduleRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LeaveAccrualScheduleCreation,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (access.companyId !== input.companyId || !canScheduleLeaveAccrual(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_operation"));
    const selected = normalizeLeaveAccrualScheduleCreation(input);
    if (!selected.ok) return Promise.resolve(selected);
    return this.schedules.create(operation, selected.value, signal);
  }
}
