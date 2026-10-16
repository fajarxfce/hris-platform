import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { OidcBinding, OidcBindingId } from "../entities/oidc-binding";
import type { OidcBindingChange } from "../entities/oidc-binding-change";
import type { OidcBindingPage } from "../entities/oidc-binding-page";

export interface OidcBindingRepository {
  list(
    accountId: AccountId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<OidcBindingPage>>;
  get(accountId: AccountId, id: OidcBindingId, signal: AbortSignal): Promise<Result<OidcBinding>>;
  save(
    operation: OperationId,
    input: OidcBindingChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
