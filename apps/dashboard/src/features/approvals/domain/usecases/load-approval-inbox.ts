import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadApprovalInbox } from "../policies/approval-read-policy";
import type { ApprovalRepository } from "../repositories/approval-repository";

export class LoadApprovalInbox {
  constructor(private readonly approvals: ApprovalRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadApprovalInbox(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.approvals.inbox(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
