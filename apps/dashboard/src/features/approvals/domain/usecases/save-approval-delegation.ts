import { type AccountId, isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalDelegationChange } from "../entities/approval-delegation-change";
import {
  normalizeApprovalDelegation,
  validApprovalDelegationChange,
} from "../policies/approval-delegation-policy";
import { canManageApprovals, canReadApprovalInbox } from "../policies/approval-read-policy";
import type { ApprovalDelegationRepository } from "../repositories/approval-delegation-repository";

export class SaveApprovalDelegation {
  constructor(private readonly delegations: ApprovalDelegationRepository) {}
  execute(
    access: CompanyAccess,
    account: AccountId,
    operation: OperationId,
    input: ApprovalDelegationChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const change = normalizeApprovalDelegation(input);
    if (
      !canReadApprovalInbox(access.permissions) ||
      (change.fromAccount !== account && !canManageApprovals(access.permissions))
    )
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation) || !validApprovalDelegationChange(change))
      return Promise.resolve(failed("invalid_delegation"));
    return this.delegations.save(access.companyId, operation, change, signal);
  }
}
