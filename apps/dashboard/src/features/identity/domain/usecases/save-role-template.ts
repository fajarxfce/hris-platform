import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { RoleTemplateChange } from "../entities/role-template-change";
import type { CompanyAccess } from "../entities/session";
import {
  canManageRoleTemplates,
  normalizeRoleTemplateChange,
  validateRoleTemplateChange,
} from "../policies/role-template-policy";
import type { RoleTemplateRepository } from "../repositories/role-template-repository";
export class SaveRoleTemplate {
  constructor(private readonly roles: RoleTemplateRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: RoleTemplateChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageRoleTemplates(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_role_template"));
    const change = normalizeRoleTemplateChange(input);
    const failure = validateRoleTemplateChange(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.roles.save(access.companyId, operation, change, signal);
  }
}
