import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseSearch } from "../entities/lifecycle-case-search";
import { normalizeLifecycleCaseSearch } from "../policies/lifecycle-case-policy";
import { canReadLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class LoadLifecycleCases {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(access: CompanyAccess, search: LifecycleCaseSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    const filter = normalizeLifecycleCaseSearch(search);
    if (!filter.ok) return Promise.resolve(filter);
    return this.cases.list(access.companyId, filter.value, signal);
  }
}
