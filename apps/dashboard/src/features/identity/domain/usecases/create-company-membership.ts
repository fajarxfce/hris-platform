import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyMembershipAssignment } from "../entities/company-membership-assignment";
import type { CompanyAccess } from "../entities/session";
import { canManageCompanyMembers } from "../policies/company-member-policy";
import {
  normalizeCompanyMembershipChange,
  validateCompanyMembershipTerms,
} from "../policies/company-membership-change-policy";
import type { CompanyMemberRepository } from "../repositories/company-member-repository";

export class CreateCompanyMembership {
  constructor(private readonly members: CompanyMemberRepository) {}

  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: CompanyMembershipAssignment,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageCompanyMembers(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_membership"));
    const change = normalizeCompanyMembershipChange({
      ...input,
      active: true,
      expectedVersion: null,
    });
    const failure = validateCompanyMembershipTerms(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    if (change.roleTemplates.some((role) => role.retainApplied))
      return Promise.resolve(failed("role_template_review_required"));
    return this.members.save(
      access.companyId,
      operation.toLowerCase() as OperationId,
      change,
      signal,
    );
  }
}
