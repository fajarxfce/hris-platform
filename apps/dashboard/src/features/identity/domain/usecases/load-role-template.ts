import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { RoleTemplateId } from "../entities/role-template";
import type { CompanyAccess } from "../entities/session";
import { canManageRoleTemplates } from "../policies/role-template-policy";
import type { RoleTemplateRepository } from "../repositories/role-template-repository";
export class LoadRoleTemplate {
  constructor(private readonly roles: RoleTemplateRepository) {}
  execute(access: CompanyAccess, id: string, historyAfter: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageRoleTemplates(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(id)) return Promise.resolve(failed("role_template_not_found"));
    if (
      historyAfter !== null &&
      (!/^(?:0|[1-9]\d{0,15})$/u.test(historyAfter) || !Number.isSafeInteger(Number(historyAfter)))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.roles.get(
      access.companyId,
      id.toLowerCase() as RoleTemplateId,
      historyAfter,
      signal,
    );
  }
}
