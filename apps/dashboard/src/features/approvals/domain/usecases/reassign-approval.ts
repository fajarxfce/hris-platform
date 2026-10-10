import { type AccountId, isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalReassignmentSelection } from "../entities/approval-reassignment";
import type { ApprovalRequest } from "../entities/approval-request";
import { canManageApprovals } from "../policies/approval-read-policy";
import {
  canReassignApproval,
  excludedApprovalAccounts,
} from "../policies/approval-reassignment-policy";
import type { ApprovalRepository } from "../repositories/approval-repository";

export class ReassignApproval {
  constructor(private readonly approvals: ApprovalRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    request: ApprovalRequest,
    input: ApprovalReassignmentSelection,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageApprovals(access.permissions) || request.companyId !== access.companyId)
      return Promise.resolve(failed("access_denied"));
    if (!canReassignApproval(access, request)) return Promise.resolve(failed("approval_changed"));
    const assignees = Object.freeze(input.assignees.map((id) => id.toLowerCase() as AccountId));
    const reason = input.reason.trim();
    if (
      !isUuid(operation) ||
      !isUuid(request.id) ||
      !Number.isSafeInteger(request.version) ||
      request.version < 0 ||
      request.version >= Number.MAX_SAFE_INTEGER ||
      assignees.length < 1 ||
      assignees.length > 25 ||
      assignees.some((id) => !isUuid(id)) ||
      new Set(assignees).size !== assignees.length ||
      reason.length < 1 ||
      reason.length > 1000
    )
      return Promise.resolve(failed("invalid_approval_reassignment"));
    const excluded = new Set(excludedApprovalAccounts(request));
    if (assignees.some((id) => excluded.has(id)))
      return Promise.resolve(failed("approver_unavailable"));
    // Retrying uses the original review snapshot; a new read could hide a committed receipt.
    return this.approvals.reassign(
      access.companyId,
      operation,
      Object.freeze({ id: request.id, version: request.version, assignees, reason }),
      signal,
    );
  }
}
