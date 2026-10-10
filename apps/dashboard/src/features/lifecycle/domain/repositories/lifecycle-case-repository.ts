import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { LifecycleCase, LifecycleCaseId, LifecycleCasePage } from "../entities/lifecycle-case";
import type { LifecycleCaseFilter } from "../entities/lifecycle-case-search";
import type { LifecycleHistoryPage } from "../entities/lifecycle-event";
import type { AssignedLifecycleTaskPage } from "../entities/lifecycle-task-context";

export interface LifecycleCaseRepository {
  list(
    company: CompanyId,
    filter: LifecycleCaseFilter,
    signal: AbortSignal,
  ): Promise<Result<LifecycleCasePage>>;
  get(company: CompanyId, id: LifecycleCaseId, signal: AbortSignal): Promise<Result<LifecycleCase>>;
  history(
    company: CompanyId,
    id: LifecycleCaseId,
    after: number | null,
    signal: AbortSignal,
  ): Promise<Result<LifecycleHistoryPage>>;
  assigned(
    company: CompanyId,
    account: AccountId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<AssignedLifecycleTaskPage>>;
}
