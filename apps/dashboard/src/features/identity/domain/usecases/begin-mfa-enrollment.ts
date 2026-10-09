import type { OperationId } from "../../../../core/domain/identifiers";
import type { IdentityRepository } from "../repositories/identity-repository";

export class BeginMfaEnrollment {
  constructor(private readonly identities: IdentityRepository) {}
  execute(operationId: OperationId, signal: AbortSignal) {
    return this.identities.enroll(operationId, signal);
  }
}
