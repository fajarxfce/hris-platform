import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTemplatePage } from "../entities/lifecycle-template-page";
import { canReadLifecycle, isLifecycleTemplateCode } from "../policies/lifecycle-template-policy";
import type { LifecycleTemplateRepository } from "../repositories/lifecycle-template-repository";

export class LoadLifecycleTemplates {
  constructor(private readonly templates: LifecycleTemplateRepository) {}
  execute(
    access: CompanyAccess,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LifecycleTemplatePage>> {
    signal.throwIfAborted();
    if (!canReadLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (after !== null && !isLifecycleTemplateCode(after))
      return Promise.resolve(failed("invalid_page"));
    return this.templates.list(access.companyId, after, signal);
  }
}
