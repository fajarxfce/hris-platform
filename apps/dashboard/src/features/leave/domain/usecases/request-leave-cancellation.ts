import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveActionSnapshot } from "../entities/leave-request-action";
import { prepareLeaveAction } from "../policies/leave-request-action-policy";
import type { LeaveRequestRepository } from "../repositories/leave-request-repository";

export class RequestLeaveCancellation {
  constructor(private readonly requests: LeaveRequestRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    review: LeaveActionSnapshot,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const command = prepareLeaveAction(access, operation, review, "cancel", reason);
    if (!command.ok) return Promise.resolve(command);
    return this.requests.requestCancellation(access.companyId, operation, command.value, signal);
  }
}
