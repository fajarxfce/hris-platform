import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ApprovalHistory } from "../entities/approval-history";
import type { ApprovalId } from "../entities/approval-request";
export interface ApprovalHistoryRepository {
  get(
    company: CompanyId,
    id: ApprovalId,
    after: number | null,
    expectedVersion: number | null,
    signal: AbortSignal,
  ): Promise<Result<ApprovalHistory>>;
}
