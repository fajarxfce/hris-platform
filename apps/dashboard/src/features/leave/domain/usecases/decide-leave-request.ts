import type { OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveActionSnapshot, LeaveRequestDecision } from "../entities/leave-request-action";
import { prepareLeaveAction } from "../policies/leave-request-action-policy";
import type { LeaveRequestRepository } from "../repositories/leave-request-repository";

export class DecideLeaveRequest {
  constructor(private readonly requests: LeaveRequestRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    review: LeaveActionSnapshot,
    decision: LeaveRequestDecision["decision"],
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (decision !== "APPROVE" && decision !== "REJECT")
      return Promise.resolve(failed("invalid_leave_decision"));
    const command = prepareLeaveAction(
      access,
      operation,
      review,
      decision === "APPROVE" ? "approve" : "reject",
      reason,
    );
    if (!command.ok) return Promise.resolve(command);
    // A receipt retry uses the original review. Re-reading could hide a committed decision.
    return this.requests.decide(
      access.companyId,
      operation,
      Object.freeze({ ...command.value, decision }),
      signal,
    );
  }
}
