import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeavePolicyChange } from "../entities/leave-policy-change";
import {
  normalizeLeavePolicyChange,
  validateLeavePolicyChange,
} from "../policies/leave-policy-change-policy";
import { canManageLeavePolicies } from "../policies/leave-read-policy";
import type { LeavePolicyRepository } from "../repositories/leave-policy-repository";

export class SaveLeavePolicy {
  constructor(private readonly policies: LeavePolicyRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LeavePolicyChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageLeavePolicies(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_leave_policy"));
    const change = normalizeLeavePolicyChange(input);
    const failure = validateLeavePolicyChange(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.policies.save(access.companyId, operation, change, signal);
  }
}
