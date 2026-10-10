import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseId } from "../entities/lifecycle-case";
import { isLifecycleHistoryCursor } from "../policies/lifecycle-case-policy";
import { canReadLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class LoadLifecycleHistory {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(access: CompanyAccess, id: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("lifecycle_case_not_found"));
    if (after !== null && !isLifecycleHistoryCursor(after))
      return Promise.resolve(failed("invalid_page"));
    return this.cases.history(
      access.companyId,
      id.toLowerCase() as LifecycleCaseId,
      after === null ? null : Number(after),
      signal,
    );
  }
}
