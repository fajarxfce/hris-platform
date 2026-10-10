import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ApprovalId, ApprovalInbox, ApprovalRequest } from "../entities/approval-request";

export interface ApprovalRepository {
  inbox(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ApprovalInbox>>;
  get(company: CompanyId, id: ApprovalId, signal: AbortSignal): Promise<Result<ApprovalRequest>>;
}
