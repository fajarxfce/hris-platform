import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveRequestId } from "../entities/leave-request";
import type { LeaveRequestIntent } from "../entities/leave-request-action";
import {
  canPerformLeaveAction,
  hasLeaveActionPermission,
} from "../policies/leave-request-action-policy";
import type { LeaveRequestRepository } from "../repositories/leave-request-repository";

export class ReviewLeaveAction {
  constructor(private readonly requests: LeaveRequestRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    intent: LeaveRequestIntent,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!hasLeaveActionPermission(access.permissions, intent)) return failed("access_denied");
    if (!isUuid(id)) return failed("leave_request_not_found");
    const result = await this.requests.get(
      access.companyId,
      id.toLowerCase() as LeaveRequestId,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (result.ok && !canPerformLeaveAction(access, result.value, intent))
      return failed("leave_action_unavailable");
    return result;
  }
}
