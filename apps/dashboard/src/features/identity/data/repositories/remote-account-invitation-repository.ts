import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { OperationId } from "../../../../core/domain/identifiers";
import type { AccountInvitation } from "../../domain/entities/account-invitation";
import type { AccountInvitationRepository } from "../../domain/repositories/account-invitation-repository";
import type { AccountInvitationDataSource } from "../datasources/account-invitation-data-source";
export class RemoteAccountInvitationRepository implements AccountInvitationRepository {
  constructor(private readonly source: AccountInvitationDataSource) {}
  invite(operation: OperationId, input: AccountInvitation, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const { accountId, ...fields } = input;
      const receipt = await this.source.invite(operation, { id: accountId, ...fields }, signal);
      if (
        receipt.id.toLowerCase() !== accountId ||
        receipt.version !== (input.expectedVersion ?? -1) + 1
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
