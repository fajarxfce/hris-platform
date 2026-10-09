import type { OperationId } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { MfaVerification } from "../entities/mfa";
import type { IdentityRepository } from "../repositories/identity-repository";

export class ConfirmMfaEnrollment {
  constructor(private readonly identities: IdentityRepository) {}

  async execute(
    operationId: OperationId,
    code: string,
    signal: AbortSignal,
  ): Promise<Result<MfaVerification>> {
    if (!/^[0-9]{6}$/u.test(code)) return failed("invalid_mfa_code");
    return this.identities.confirm(operationId, code, signal);
  }
}
