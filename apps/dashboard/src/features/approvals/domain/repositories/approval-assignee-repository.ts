import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ApprovalAssigneePage, ApprovalAssigneeSearch } from "../entities/approval-assignee";

export interface ApprovalAssigneeRepository {
  list(
    company: CompanyId,
    search: ApprovalAssigneeSearch,
    signal: AbortSignal,
  ): Promise<Result<ApprovalAssigneePage>>;
}
