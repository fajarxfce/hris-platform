import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseId } from "../entities/lifecycle-case";
import { canReadLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class LoadLifecycleCase {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("lifecycle_case_not_found"));
    return this.cases.get(access.companyId, id.toLowerCase() as LifecycleCaseId, signal);
  }
}
