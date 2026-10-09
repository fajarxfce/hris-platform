import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { SignInCredentials } from "../../domain/entities/sign-in";
import type { IdentityRepository } from "../../domain/repositories/identity-repository";
import type { IdentityDataSource } from "../datasources/identity-data-source";
import {
  toCompanyAccess,
  toIdentityProviders,
  toMfaEnrollment,
  toMfaVerification,
  toSession,
} from "../mappers/identity-mapper";

export class RemoteIdentityRepository implements IdentityRepository {
  constructor(private readonly source: IdentityDataSource) {}

  session(signal: AbortSignal) {
    return safeHttpCall(signal, async () => toSession(await this.source.session(signal)));
  }
  access(company: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toCompanyAccess(await this.source.access(company, signal)),
    );
  }
  signIn(credentials: SignInCredentials, signal: AbortSignal) {
    return safeHttpCall(signal, () =>
      this.source.signIn(credentials.email, credentials.password, signal),
    );
  }
  signOut(signal: AbortSignal) {
    return safeHttpCall(signal, () => this.source.signOut(signal));
  }
  providers(signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toIdentityProviders(await this.source.providers(signal)),
    );
  }
  enroll(operation: OperationId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toMfaEnrollment(await this.source.enroll(operation, signal)),
    );
  }
  confirm(operation: OperationId, code: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toMfaVerification(await this.source.confirm(operation, code, signal)),
    );
  }
  verify(code: string, recovery: boolean, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toMfaVerification(await this.source.verify(code, recovery, signal)),
    );
  }
}
