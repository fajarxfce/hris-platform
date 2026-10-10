import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LifecycleCase, LifecycleCaseId, LifecycleCasePage } from "../entities/lifecycle-case";
import type { LifecycleCaseFilter } from "../entities/lifecycle-case-search";
import type { LifecycleHistoryPage } from "../entities/lifecycle-event";
import type { LifecycleTaskChange } from "../entities/lifecycle-task-change";
import type { AssignedLifecycleTaskPage } from "../entities/lifecycle-task-context";

export interface LifecycleCaseRepository {
  changeTask(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleTaskChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
