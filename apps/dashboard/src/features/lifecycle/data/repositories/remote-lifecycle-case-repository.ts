import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { LifecycleCaseChange } from "../../domain/entities/lifecycle-case-change";
import type { LifecycleCaseFilter } from "../../domain/entities/lifecycle-case-search";
import type { LifecycleCaseStart } from "../../domain/entities/lifecycle-case-start";
import type { LifecycleTaskAssignment } from "../../domain/entities/lifecycle-task-assignment";
import type { LifecycleTaskChange } from "../../domain/entities/lifecycle-task-change";
import type { LifecycleCaseRepository } from "../../domain/repositories/lifecycle-case-repository";
import type { LifecycleCaseDataSource } from "../datasources/lifecycle-case-data-source";
import { toAssignedLifecycleTaskPage } from "../mappers/assigned-lifecycle-task-mapper";
import { toLifecycleAssigneePage } from "../mappers/lifecycle-assignee-mapper";
import {
  toLifecycleCaseChangeDto,
  toLifecycleCaseReceipt,
} from "../mappers/lifecycle-case-change-mapper";
import { toLifecycleCaseDetails, toLifecycleCasePage } from "../mappers/lifecycle-case-mapper";
import {
  toLifecycleCaseStartDto,
  toLifecycleCaseStartReceipt,
} from "../mappers/lifecycle-case-start-mapper";
import { toLifecycleHistory } from "../mappers/lifecycle-history-mapper";
import { toLifecycleTaskAssignmentDto } from "../mappers/lifecycle-task-assignment-mapper";
import { toLifecycleTaskChangeDto } from "../mappers/lifecycle-task-change-mapper";

export class RemoteLifecycleCaseRepository implements LifecycleCaseRepository {
  constructor(private readonly source: LifecycleCaseDataSource) {}
  cancel(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleCaseChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLifecycleCaseReceipt(
        await this.source.cancel(
          company,
          change.caseId,
          operation,
          toLifecycleCaseChangeDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  completeOnboarding(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleCaseChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLifecycleCaseReceipt(
        await this.source.completeOnboarding(
          company,
          change.caseId,
          operation,
          toLifecycleCaseChangeDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  start(
    company: CompanyId,
    operation: OperationId,
    command: LifecycleCaseStart,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLifecycleCaseStartReceipt(
        await this.source.start(company, operation, toLifecycleCaseStartDto(command), signal),
        command.id,
      ),
    );
  }
  assignees(company: CompanyId, query: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLifecycleAssigneePage(
        await this.source.assignees(company, query, after, signal),
        company,
        after,
      ),
    );
  }
  assignTask(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleTaskAssignment,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLifecycleCaseReceipt(
        await this.source.assignTask(
          company,
          change.caseId,
          change.taskKey,
          operation,
          toLifecycleTaskAssignmentDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  changeTask(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleTaskChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLifecycleCaseReceipt(
        await this.source.changeTask(
          company,
          change.caseId,
          change.taskKey,
          operation,
          toLifecycleTaskChangeDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  list(company: CompanyId, filter: LifecycleCaseFilter, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLifecycleCasePage(await this.source.list(company, filter, signal), company, filter),
    );
  }
  get(company: CompanyId, id: LifecycleCaseId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLifecycleCaseDetails(await this.source.get(company, id, signal), company, id),
    );
  }
  history(company: CompanyId, id: LifecycleCaseId, after: number | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLifecycleHistory(await this.source.history(company, id, after, signal), after),
    );
  }
  assigned(company: CompanyId, account: AccountId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAssignedLifecycleTaskPage(
        await this.source.assigned(company, after, signal),
        company,
        account,
        after,
      ),
    );
  }
}
