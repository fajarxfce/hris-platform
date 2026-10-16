import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../entities/session";
import { canManageRoleTemplates } from "../policies/role-template-policy";
import type { RoleTemplateRepository } from "../repositories/role-template-repository";
export class LoadRoleTemplates {
  constructor(private readonly roles: RoleTemplateRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageRoleTemplates(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.roles.list(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
