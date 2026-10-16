import { z } from "zod";
import type { HttpClient } from "../../../../core/data/http/http-client";
import type { AccountCredentialDataSource } from "./account-credential-data-source";
export class HttpAccountCredentialDataSource implements AccountCredentialDataSource {
  constructor(private readonly http: HttpClient) {}
  async recover(email: string, signal: AbortSignal): Promise<void> {
    z.null().parse(
      await this.http.request(
        {
          path: "/api/v1/auth/password-recovery",
          method: "POST",
          response: { type: "empty", status: 202 },
          body: { email },
        },
        signal,
      ),
    );
  }
  async acceptInvitation(token: string, password: string, signal: AbortSignal): Promise<void> {
    z.null().parse(
      await this.http.request(
        {
          path: "/api/v1/auth/invitations/accept",
          method: "POST",
          response: { type: "empty", status: 204 },
          body: { token, password },
        },
        signal,
      ),
    );
  }
  async resetPassword(token: string, password: string, signal: AbortSignal): Promise<void> {
    z.null().parse(
      await this.http.request(
        {
          path: "/api/v1/auth/password-recovery/confirm",
          method: "POST",
          response: { type: "empty", status: 204 },
          body: { token, password },
        },
        signal,
      ),
    );
  }
}
