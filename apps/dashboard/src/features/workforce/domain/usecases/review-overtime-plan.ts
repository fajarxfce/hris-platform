import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { canPlanOvertime, isOvertimeWorkDate } from "../policies/overtime-planning-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class ReviewOvertimePlan {
  constructor(private readonly requests: OvertimeRepository) {}
  execute(access: CompanyAccess, employeeId: string, workDate: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canPlanOvertime(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employeeId)) return Promise.resolve(failed("employee_not_found"));
    if (!isOvertimeWorkDate(workDate)) return Promise.resolve(failed("overtime_date_out_of_range"));
    return this.requests.planningReview(
      access.companyId,
      employeeId.toLowerCase() as EmployeeId,
      workDate,
      signal,
    );
  }
}
