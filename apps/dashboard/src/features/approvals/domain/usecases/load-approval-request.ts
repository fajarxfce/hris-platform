import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalId } from "../entities/approval-request";
import type { ApprovalRepository } from "../repositories/approval-repository";

export class LoadApprovalRequest {
  constructor(private readonly approvals: ApprovalRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!isUuid(id)) return Promise.resolve(failed("approval_not_found"));
    // Authors and beneficiaries can read their own request without an inbox grant.
    // Assignment, delegation and ownership are resolved by the backend.
    return this.approvals.get(access.companyId, id.toLowerCase() as ApprovalId, signal);
  }
}
