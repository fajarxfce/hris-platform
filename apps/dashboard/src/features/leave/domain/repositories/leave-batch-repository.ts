import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LeaveBatchId, LeaveBatchKind, LeaveBatchPage } from "../entities/leave-batch";
import type { LeaveBatchCreation, LeaveBatchResume } from "../entities/leave-batch-command";
import type { LeaveBatchDetails } from "../entities/leave-batch-details";
import type { LeaveBatchReview, LeaveBatchSelection } from "../entities/leave-batch-review";
import type { LeavePolicyPage } from "../entities/leave-policy-definition";
export interface LeaveBatchRepository {
  types(
    company: CompanyId,
    kind: LeaveBatchKind,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeavePolicyPage>>;
  review(
    company: CompanyId,
    selection: LeaveBatchSelection,
    signal: AbortSignal,
  ): Promise<Result<LeaveBatchReview>>;
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveBatchPage>>;
  get(
    company: CompanyId,
    id: LeaveBatchId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveBatchDetails>>;
  start(
    operation: OperationId,
    input: LeaveBatchCreation,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  resume(
    operation: OperationId,
    input: LeaveBatchResume,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
