import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { LeavePolicyId, LeavePolicyPage } from "../entities/leave-policy-definition";
import type { LeavePolicyReview } from "../entities/leave-policy-review";

export interface LeavePolicyRepository {
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
