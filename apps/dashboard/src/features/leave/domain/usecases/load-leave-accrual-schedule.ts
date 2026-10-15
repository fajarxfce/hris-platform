import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAccrualScheduleId } from "../entities/leave-accrual-schedule";
import { canReadLeaveBatches } from "../policies/leave-batch-policy";
import type { LeaveAccrualScheduleRepository } from "../repositories/leave-accrual-schedule-repository";
export class LoadLeaveAccrualSchedule {
  constructor(private readonly schedules: LeaveAccrualScheduleRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLeaveBatches(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("invalid_leave_accrual_schedule"));
    return this.schedules.get(access.companyId, id.toLowerCase() as LeaveAccrualScheduleId, signal);
  }
}
