import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  canReadAssignedLifecycle,
  isAssignedLifecycleCursor,
} from "../policies/lifecycle-case-policy";
import type { LifecycleCaseRepository } from "../repositories/lifecycle-case-repository";

export class LoadAssignedLifecycleTasks {
  constructor(private readonly cases: LifecycleCaseRepository) {}
  execute(access: CompanyAccess, account: AccountId, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadAssignedLifecycle(access.permissions))
      return Promise.resolve(failed("lifecycle_access_required"));
    if (after !== null && !isAssignedLifecycleCursor(after))
      return Promise.resolve(failed("invalid_page"));
    return this.cases.assigned(
      access.companyId,
      account,
      after === null ? null : `${after.slice(0, 36).toLowerCase()}${after.slice(36)}`,
      signal,
    );
  }
}
