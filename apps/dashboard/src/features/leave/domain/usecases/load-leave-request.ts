import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveRequestId } from "../entities/leave-request";
import type { LeaveRequestRepository } from "../repositories/leave-request-repository";

export class LoadLeaveRequest {
  constructor(private readonly requests: LeaveRequestRepository) {}
  execute(access: CompanyAccess, id: string, historyAfter: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!isUuid(id)) return Promise.resolve(failed("leave_request_not_found"));
    if (
      historyAfter !== null &&
      (!/^(0|[1-9]\d{0,15})$/u.test(historyAfter) || !Number.isSafeInteger(Number(historyAfter)))
    )
      return Promise.resolve(failed("invalid_page"));
    // The server resolves current employee scope, ownership, assignment and delegation.
    return this.requests.get(
      access.companyId,
      id.toLowerCase() as LeaveRequestId,
      historyAfter,
      signal,
    );
  }
}
