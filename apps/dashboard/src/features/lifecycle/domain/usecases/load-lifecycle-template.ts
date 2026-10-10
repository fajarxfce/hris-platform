import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTemplate, LifecycleTemplateId } from "../entities/lifecycle-template";
import { canReadLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleTemplateRepository } from "../repositories/lifecycle-template-repository";

export class LoadLifecycleTemplate {
  constructor(private readonly templates: LifecycleTemplateRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<LifecycleTemplate>> {
    signal.throwIfAborted();
    if (!canReadLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("lifecycle_template_not_found"));
    return this.templates.get(access.companyId, id.toLowerCase() as LifecycleTemplateId, signal);
  }
}
