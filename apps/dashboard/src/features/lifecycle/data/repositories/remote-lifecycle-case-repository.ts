import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { LifecycleCaseFilter } from "../../domain/entities/lifecycle-case-search";
import type { LifecycleTaskChange } from "../../domain/entities/lifecycle-task-change";
import type { LifecycleCaseRepository } from "../../domain/repositories/lifecycle-case-repository";
import type { LifecycleCaseDataSource } from "../datasources/lifecycle-case-data-source";
import { toAssignedLifecycleTaskPage } from "../mappers/assigned-lifecycle-task-mapper";
import { toLifecycleCaseDetails, toLifecycleCasePage } from "../mappers/lifecycle-case-mapper";
import { toLifecycleHistory } from "../mappers/lifecycle-history-mapper";
import {
  toLifecycleTaskChangeDto,
  toLifecycleTaskReceipt,
} from "../mappers/lifecycle-task-change-mapper";

export class RemoteLifecycleCaseRepository implements LifecycleCaseRepository {
  constructor(private readonly source: LifecycleCaseDataSource) {}
  changeTask(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleTaskChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLifecycleTaskReceipt(
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
