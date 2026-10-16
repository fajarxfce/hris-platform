import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { AccountAccessChange } from "../entities/account-access-change";
import type { ManagedAccountPage } from "../entities/managed-account-page";
import type { ManagedAccountReview } from "../entities/managed-account-review";
export interface ManagedAccountRepository {
  list(
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ManagedAccountPage>>;
  get(id: AccountId, signal: AbortSignal): Promise<Result<ManagedAccountReview>>;
  save(
    operation: OperationId,
    input: AccountAccessChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
