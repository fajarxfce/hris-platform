import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalId } from "../entities/approval-request";
import { canManageApprovals } from "../policies/approval-read-policy";
import { canReassignApproval } from "../policies/approval-reassignment-policy";
import type { ApprovalRepository } from "../repositories/approval-repository";

export class ReviewApprovalReassignment {
  constructor(private readonly approvals: ApprovalRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageApprovals(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("approval_not_found");
    const result = await this.approvals.get(
      access.companyId,
      id.toLowerCase() as ApprovalId,
      signal,
    );
    signal.throwIfAborted();
    if (result.ok && !canReassignApproval(access, result.value)) return failed("approval_changed");
    return result;
  }
}
