import { failed, type Result } from "../../../../core/domain/result";
import type { MfaVerification } from "../entities/mfa";
import type { IdentityRepository } from "../repositories/identity-repository";

export class VerifyMfa {
  constructor(private readonly identities: IdentityRepository) {}

  async execute(
    code: string,
    recovery: boolean,
    signal: AbortSignal,
  ): Promise<Result<MfaVerification>> {
    if (recovery ? code.trim().length < 6 || code.length > 100 : !/^[0-9]{6}$/u.test(code))
      return failed("invalid_mfa_code");
    return this.identities.verify(code, recovery, signal);
  }
}
