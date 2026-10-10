import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { LifecycleCaseFilter } from "../../domain/entities/lifecycle-case-search";
import type { LifecycleCaseRepository } from "../../domain/repositories/lifecycle-case-repository";
import type { LifecycleCaseDataSource } from "../datasources/lifecycle-case-data-source";
import { toAssignedLifecycleTaskPage } from "../mappers/assigned-lifecycle-task-mapper";
import { toLifecycleCaseDetails, toLifecycleCasePage } from "../mappers/lifecycle-case-mapper";
import { toLifecycleHistory } from "../mappers/lifecycle-history-mapper";

export class RemoteLifecycleCaseRepository implements LifecycleCaseRepository {
  constructor(private readonly source: LifecycleCaseDataSource) {}
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
