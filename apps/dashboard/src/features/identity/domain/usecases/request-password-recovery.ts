import { validAccountEmail } from "../policies/account-invitation-policy";
import type { AccountCredentialRepository } from "../repositories/account-credential-repository";
export class RequestPasswordRecovery {
  constructor(private readonly credentials: AccountCredentialRepository) {}
  execute(email: string, signal: AbortSignal) {
    signal.throwIfAborted();
    const normalized = email.trim().toLowerCase();
    if (!validAccountEmail(normalized))
      return Promise.resolve({
        ok: false as const,
        failure: {
          code: "invalid_recovery_request",
          fields: { email: "invalid_email" },
          parameters: {},
        },
      });
    return this.credentials.recover(normalized, signal);
  }
}
