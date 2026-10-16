import type { HttpClient } from "../../../../core/data/http/http-client";
import { type AccountInvitationDto, invitationReceiptDto } from "../models/account-invitation-dto";
import type { AccountInvitationDataSource } from "./account-invitation-data-source";
export class HttpAccountInvitationDataSource implements AccountInvitationDataSource {
  constructor(private readonly http: HttpClient) {}
  async invite(operation: string, input: AccountInvitationDto, signal: AbortSignal) {
    return invitationReceiptDto.parse(
      await this.http.request(
        {
          path: "/api/v1/identity/invitations",
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
