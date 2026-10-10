import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LifecycleAssigneePage } from "../entities/lifecycle-assignee";
import type { LifecycleCase, LifecycleCaseId, LifecycleCasePage } from "../entities/lifecycle-case";
import type { LifecycleCaseChange } from "../entities/lifecycle-case-change";
import type { LifecycleCaseFilter } from "../entities/lifecycle-case-search";
import type { LifecycleCaseStart } from "../entities/lifecycle-case-start";
import type { LifecycleHistoryPage } from "../entities/lifecycle-event";
import type { LifecycleTaskAssignment } from "../entities/lifecycle-task-assignment";
import type { LifecycleTaskChange } from "../entities/lifecycle-task-change";
import type { AssignedLifecycleTaskPage } from "../entities/lifecycle-task-context";
import type { OffboardingCompletion } from "../entities/offboarding-completion";
import type { OffboardingReview } from "../entities/offboarding-review";

export interface LifecycleCaseRepository {
  offboardingReview(
    company: CompanyId,
    id: LifecycleCaseId,
    signal: AbortSignal,
  ): Promise<Result<OffboardingReview>>;
  completeOffboarding(
    company: CompanyId,
    operation: OperationId,
    change: OffboardingCompletion,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  cancel(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleCaseChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  completeOnboarding(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleCaseChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  start(
    company: CompanyId,
    operation: OperationId,
    command: LifecycleCaseStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  assignees(
    company: CompanyId,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LifecycleAssigneePage>>;
  assignTask(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleTaskAssignment,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
