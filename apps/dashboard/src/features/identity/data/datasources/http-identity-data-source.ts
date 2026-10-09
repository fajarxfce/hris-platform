import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  companyAccessDto,
  identityProvidersDto,
  mfaEnrollmentDto,
  mfaVerificationDto,
  sessionDto,
} from "../models/identity-dto";
import type { IdentityDataSource } from "./identity-data-source";

export class HttpIdentityDataSource implements IdentityDataSource {
  constructor(private readonly http: HttpClient) {}

  async session(signal: AbortSignal) {
    return sessionDto.parse(await this.http.request({ path: "/api/v1/me" }, signal));
  }

  async access(company: string, signal: AbortSignal) {
    return companyAccessDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${encodeURIComponent(company)}/me/access` },
        signal,
      ),
    );
  }

  async signIn(email: string, password: string, signal: AbortSignal): Promise<void> {
    await this.http.request(
      { path: "/api/v1/auth/login", method: "POST", body: { email, password } },
      signal,
    );
  }

  async signOut(signal: AbortSignal): Promise<void> {
    await this.http.request({ path: "/api/v1/auth/logout", method: "POST" }, signal);
  }

  async providers(signal: AbortSignal) {
    return identityProvidersDto.parse(
      await this.http.request({ path: "/api/v1/auth/providers" }, signal),
    );
  }

  async enroll(operationId: string, signal: AbortSignal) {
    return mfaEnrollmentDto.parse(
      await this.http.request(
        { path: "/api/v1/auth/mfa/enrollment", method: "POST", operationId },
        signal,
      ),
    );
  }

  async confirm(operationId: string, code: string, signal: AbortSignal) {
    return mfaVerificationDto.parse(
      await this.http.request(
        {
          path: "/api/v1/auth/mfa/enrollment/confirm",
          method: "POST",
          body: { operationId, code },
        },
        signal,
      ),
    );
  }

  async verify(code: string, recovery: boolean, signal: AbortSignal) {
    return mfaVerificationDto.parse(
      await this.http.request(
        { path: "/api/v1/auth/mfa/verify", method: "POST", body: { code, recovery } },
        signal,
      ),
    );
  }
}
