import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { MfaEnrollment, MfaVerification } from "../entities/mfa";
import type { CompanyAccess, Session } from "../entities/session";
import type { IdentityProvider, SignInCredentials } from "../entities/sign-in";

export interface IdentityRepository {
  session(signal: AbortSignal): Promise<Result<Session>>;
  access(company: CompanyId, signal: AbortSignal): Promise<Result<CompanyAccess>>;
  signIn(credentials: SignInCredentials, signal: AbortSignal): Promise<Result<void>>;
  signOut(signal: AbortSignal): Promise<Result<void>>;
  providers(signal: AbortSignal): Promise<Result<readonly IdentityProvider[]>>;
  enroll(operation: OperationId, signal: AbortSignal): Promise<Result<MfaEnrollment>>;
  confirm(
    operation: OperationId,
    code: string,
    signal: AbortSignal,
  ): Promise<Result<MfaVerification>>;
  verify(code: string, recovery: boolean, signal: AbortSignal): Promise<Result<MfaVerification>>;
}
