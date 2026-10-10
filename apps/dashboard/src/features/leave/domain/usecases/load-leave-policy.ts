import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeavePolicyId } from "../entities/leave-policy-definition";
import { canManageLeavePolicies } from "../policies/leave-read-policy";
import type { LeavePolicyRepository } from "../repositories/leave-policy-repository";

export class LoadLeavePolicy {
  constructor(private readonly policies: LeavePolicyRepository) {}
  execute(access: CompanyAccess, id: string, historyAfter: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageLeavePolicies(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("leave_type_not_found"));
    if (
      historyAfter !== null &&
      (!/^(?:0|[1-9]\d{0,15})$/u.test(historyAfter) || !Number.isSafeInteger(Number(historyAfter)))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.policies.get(
      access.companyId,
      id.toLowerCase() as LeavePolicyId,
      historyAfter,
      signal,
    );
  }
}
