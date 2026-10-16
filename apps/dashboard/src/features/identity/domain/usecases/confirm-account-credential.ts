import { failed } from "../../../../core/domain/result";
import type { AccountCredential, AccountCredentialKind } from "../entities/account-credential";
import { validCredentialToken } from "../policies/account-credential-policy";
import type { AccountCredentialRepository } from "../repositories/account-credential-repository";
export class ConfirmAccountCredential {
  constructor(private readonly credentials: AccountCredentialRepository) {}
  execute(kind: AccountCredentialKind, input: AccountCredential, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!["invitation", "passwordRecovery"].includes(kind) || !validCredentialToken(input.token))
      return Promise.resolve(failed("credential_link_unavailable"));
    if (input.password.length < 12 || input.password.length > 128)
      return Promise.resolve({
        ok: false as const,
        failure: {
          code: "password_length",
          fields: { password: "length_12_128" },
          parameters: { minimumLength: "12", maximumLength: "128" },
        },
      });
    return this.credentials.confirm(
      kind,
      Object.freeze({ token: input.token, password: input.password }),
      signal,
    );
  }
}
