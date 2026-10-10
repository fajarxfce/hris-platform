import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalAssigneeSearch } from "../../domain/entities/approval-assignee";
import type { ApprovalAssigneeRepository } from "../../domain/repositories/approval-assignee-repository";
import type { ApprovalAdministrationDataSource } from "../datasources/approval-administration-data-source";
import { toApprovalAssigneePage } from "../mappers/approval-assignee-mapper";

export class RemoteApprovalAssigneeRepository implements ApprovalAssigneeRepository {
  constructor(private readonly source: ApprovalAdministrationDataSource) {}
  list(company: CompanyId, search: ApprovalAssigneeSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toApprovalAssigneePage(await this.source.assignees(company, search, signal), search.after),
    );
  }
}
