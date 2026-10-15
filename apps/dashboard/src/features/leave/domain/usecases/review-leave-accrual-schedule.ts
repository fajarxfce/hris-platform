import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAccrualScheduleSelection } from "../entities/leave-accrual-schedule-review";
import {
  canScheduleLeaveAccrual,
  normalizeLeaveAccrualScheduleSelection,
} from "../policies/leave-accrual-schedule-policy";
import type { LeaveAccrualScheduleRepository } from "../repositories/leave-accrual-schedule-repository";
export class ReviewLeaveAccrualSchedule {
  constructor(private readonly schedules: LeaveAccrualScheduleRepository) {}
  execute(access: CompanyAccess, input: LeaveAccrualScheduleSelection, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canScheduleLeaveAccrual(access.permissions))
      return Promise.resolve(failed("access_denied"));
    const selected = normalizeLeaveAccrualScheduleSelection(input);
    if (!selected.ok) return Promise.resolve(selected);
    return this.schedules.review(access.companyId, selected.value, signal);
  }
}
