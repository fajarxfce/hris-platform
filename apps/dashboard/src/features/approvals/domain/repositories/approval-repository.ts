import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { ApprovalReassignment } from "../entities/approval-reassignment";
import type { ApprovalId, ApprovalInbox, ApprovalRequest } from "../entities/approval-request";

export interface ApprovalRepository {
  inbox(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ApprovalInbox>>;
  get(company: CompanyId, id: ApprovalId, signal: AbortSignal): Promise<Result<ApprovalRequest>>;
  reassign(
    company: CompanyId,
    operation: OperationId,
    change: ApprovalReassignment,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
