import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LeavePolicyChange } from "../entities/leave-policy-change";
import type { LeavePolicyId, LeavePolicyPage } from "../entities/leave-policy-definition";
import type { LeavePolicyReview } from "../entities/leave-policy-review";

export interface LeavePolicyRepository {
  save(
    company: CompanyId,
    operation: OperationId,
    change: LeavePolicyChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  list(
    company: CompanyId,
    active: boolean | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeavePolicyPage>>;
  get(
    company: CompanyId,
    id: LeavePolicyId,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeavePolicyReview>>;
}
