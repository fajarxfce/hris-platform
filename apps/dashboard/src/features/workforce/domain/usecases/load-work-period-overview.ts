import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadWorkPeriods, isWorkPeriodMonth } from "../policies/work-period-policy";
import type { WorkPeriodRepository } from "../repositories/work-period-repository";

export class LoadWorkPeriodOverview {
  constructor(private readonly periods: WorkPeriodRepository) {}
  execute(access: CompanyAccess, month: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadWorkPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    return isWorkPeriodMonth(month)
      ? this.periods.overview(access.companyId, month, signal)
      : Promise.resolve(failed("invalid_work_period_range"));
  }
}
