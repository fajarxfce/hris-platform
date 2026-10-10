import { type AccountId, isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalDelegationId } from "../entities/approval-delegation";
import { canEditApprovalDelegation } from "../policies/approval-delegation-policy";
import { canReadApprovalInbox } from "../policies/approval-read-policy";
import type { ApprovalDelegationRepository } from "../repositories/approval-delegation-repository";

export class LoadApprovalDelegationForEdit {
  constructor(private readonly delegations: ApprovalDelegationRepository) {}
  async execute(access: CompanyAccess, account: AccountId, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadApprovalInbox(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("approval_delegation_not_found");
    const result = await this.delegations.get(
      access.companyId,
      id.toLowerCase() as ApprovalDelegationId,
      signal,
    );
    signal.throwIfAborted();
    if (result.ok && !canEditApprovalDelegation(access, account, result.value))
      return failed("access_denied");
    return result;
  }
}
