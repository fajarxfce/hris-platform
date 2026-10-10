import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type LeaveRequestQuery, leaveStatuses } from "../entities/leave-request";
import { canBrowseCompanyLeave, canBrowseEmployeeLeave } from "../policies/leave-read-policy";
import type { LeaveRequestRepository } from "../repositories/leave-request-repository";

export class LoadLeaveRequests {
  constructor(private readonly requests: LeaveRequestRepository) {}
  execute(access: CompanyAccess, query: LeaveRequestQuery, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canBrowseEmployeeLeave(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (query.employeeId === null && !canBrowseCompanyLeave(access.permissions))
      return Promise.resolve(failed("employee_scope_required"));
    if (
      (query.employeeId !== null && !isUuid(query.employeeId)) ||
      (query.after !== null && !isUuid(query.after)) ||
      (query.status !== null && !leaveStatuses.includes(query.status))
    )
      return Promise.resolve(failed("invalid_leave_query"));
    return this.requests.list(
      access.companyId,
      Object.freeze({
        employeeId: query.employeeId?.toLowerCase() ?? null,
        status: query.status,
        after: query.after?.toLowerCase() ?? null,
      }),
      signal,
    );
  }
}
