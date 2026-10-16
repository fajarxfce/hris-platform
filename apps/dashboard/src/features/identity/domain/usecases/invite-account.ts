import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { AccountInvitation } from "../entities/account-invitation";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import {
  accountInvitationFailure,
  normalizeAccountInvitation,
} from "../policies/account-invitation-policy";
import type { AccountInvitationRepository } from "../repositories/account-invitation-repository";
export class InviteAccount {
  constructor(private readonly invitations: AccountInvitationRepository) {}
  execute(
    access: PlatformAccess,
    operation: OperationId,
    fields: AccountInvitation,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access))
      return Promise.resolve(failed("platform_administrator_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_operation_id"));
    const input = normalizeAccountInvitation(fields);
    const failure = accountInvitationFailure(input);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.invitations.invite(operation.toLowerCase() as OperationId, input, signal);
  }
}
