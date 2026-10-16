import type { Result } from "../../../../core/domain/result";
import type { AccountCredential, AccountCredentialKind } from "../entities/account-credential";
export interface AccountCredentialRepository {
  recover(email: string, signal: AbortSignal): Promise<Result<void>>;
  confirm(
    kind: AccountCredentialKind,
    input: AccountCredential,
    signal: AbortSignal,
  ): Promise<Result<void>>;
}
