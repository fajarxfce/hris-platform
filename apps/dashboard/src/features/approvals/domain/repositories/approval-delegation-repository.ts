import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  ApprovalDelegation,
  ApprovalDelegationId,
  ApprovalDelegationPage,
} from "../entities/approval-delegation";
import type { ApprovalDelegationChange } from "../entities/approval-delegation-change";

export interface ApprovalDelegationRepository {
  list(
    company: CompanyId,
    account: AccountId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ApprovalDelegationPage>>;
  get(
    company: CompanyId,
    id: ApprovalDelegationId,
    signal: AbortSignal,
  ): Promise<Result<ApprovalDelegation>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: ApprovalDelegationChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
