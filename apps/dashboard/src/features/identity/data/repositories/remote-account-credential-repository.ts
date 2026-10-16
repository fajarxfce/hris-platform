import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type {
  AccountCredential,
  AccountCredentialKind,
} from "../../domain/entities/account-credential";
import type { AccountCredentialRepository } from "../../domain/repositories/account-credential-repository";
import type { AccountCredentialDataSource } from "../datasources/account-credential-data-source";
export class RemoteAccountCredentialRepository implements AccountCredentialRepository {
  constructor(private readonly source: AccountCredentialDataSource) {}
  recover(email: string, signal: AbortSignal) {
    return safeHttpCall(signal, () => this.source.recover(email, signal));
  }
  confirm(kind: AccountCredentialKind, input: AccountCredential, signal: AbortSignal) {
    return safeHttpCall(signal, () =>
      kind === "invitation"
        ? this.source.acceptInvitation(input.token, input.password, signal)
        : this.source.resetPassword(input.token, input.password, signal),
    );
  }
}
