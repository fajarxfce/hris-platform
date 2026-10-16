import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyMembershipChange } from "../entities/company-membership-change";
import type { CompanyAccess } from "../entities/session";
import { canManageCompanyMembers } from "../policies/company-member-policy";
import {
  normalizeCompanyMembershipChange,
  validateCompanyMembershipChange,
} from "../policies/company-membership-change-policy";
import type { CompanyMemberRepository } from "../repositories/company-member-repository";

export class SaveCompanyMembership {
  constructor(private readonly members: CompanyMemberRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: CompanyMembershipChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageCompanyMembers(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_membership"));
    const change = normalizeCompanyMembershipChange(input);
    const failure = validateCompanyMembershipChange(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.members.save(access.companyId, operation, change, signal);
  }
}
