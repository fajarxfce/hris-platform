import type {
  CompanyAccessDto,
  IdentityProvidersDto,
  MfaEnrollmentDto,
  MfaVerificationDto,
  SessionDto,
} from "../models/identity-dto";

export interface IdentityDataSource {
  session(signal: AbortSignal): Promise<SessionDto>;
  access(company: string, signal: AbortSignal): Promise<CompanyAccessDto>;
  signIn(email: string, password: string, signal: AbortSignal): Promise<void>;
  signOut(signal: AbortSignal): Promise<void>;
  providers(signal: AbortSignal): Promise<IdentityProvidersDto>;
  enroll(operation: string, signal: AbortSignal): Promise<MfaEnrollmentDto>;
  confirm(operation: string, code: string, signal: AbortSignal): Promise<MfaVerificationDto>;
  verify(code: string, recovery: boolean, signal: AbortSignal): Promise<MfaVerificationDto>;
}
