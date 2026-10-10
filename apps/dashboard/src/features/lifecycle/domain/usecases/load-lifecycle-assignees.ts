import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleAssigneePage } from "../entities/lifecycle-assignee";
import { canManageLifecycle } from "../policies/lifecycle-template-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class LoadLifecycleAssignees {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(
    access: CompanyAccess,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LifecycleAssigneePage>> {
    signal.throwIfAborted();
    if (!canManageLifecycle(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (query.length > 120 || (after !== null && !isUuid(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.cases.assignees(
      access.companyId,
      query.trim(),
      after?.toLowerCase() ?? null,
      signal,
    );
  }
}
