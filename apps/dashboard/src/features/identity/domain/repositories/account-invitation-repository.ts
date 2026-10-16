import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { AccountInvitation } from "../entities/account-invitation";
export interface AccountInvitationRepository {
  invite(
    operation: OperationId,
    input: AccountInvitation,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
