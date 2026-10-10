import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTemplateChange } from "../entities/lifecycle-template-change";
import { normalizeLifecycleTemplateChange } from "../policies/lifecycle-template-change-policy";
import { canManageLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleTemplateRepository } from "../repositories/lifecycle-template-repository";

export class SaveLifecycleTemplate {
  constructor(private readonly templates: LifecycleTemplateRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LifecycleTemplateChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_lifecycle_template"));
    const change = normalizeLifecycleTemplateChange(input);
    if (!change.ok) return Promise.resolve(change);
    return this.templates.save(access.companyId, operation, change.value, signal);
  }
}
