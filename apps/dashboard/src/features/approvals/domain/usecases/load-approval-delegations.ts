import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadApprovalInbox } from "../policies/approval-read-policy";
import type { ApprovalDelegationRepository } from "../repositories/approval-delegation-repository";

export class LoadApprovalDelegations {
  constructor(private readonly delegations: ApprovalDelegationRepository) {}
  execute(access: CompanyAccess, account: AccountId, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadApprovalInbox(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(account) || (after !== null && !isUuid(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.delegations.list(access.companyId, account, after?.toLowerCase() ?? null, signal);
  }
}
