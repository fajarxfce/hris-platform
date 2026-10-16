import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { AccountAccessChange } from "../entities/account-access-change";
import type { PlatformAccess } from "../entities/platform-access";
import {
  accountAccessFailure,
  canManagePlatformAccounts,
  normalizeAccountAccessChange,
} from "../policies/account-access-policy";
import type { ManagedAccountRepository } from "../repositories/managed-account-repository";
export class SaveAccountAccess {
  constructor(private readonly accounts: ManagedAccountRepository) {}
  execute(
    access: PlatformAccess,
    operation: OperationId,
    fields: AccountAccessChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access))
      return Promise.resolve(failed("platform_administrator_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_operation_id"));
    const input = normalizeAccountAccessChange(fields);
    const failure = accountAccessFailure(access, input);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.accounts.save(operation.toLowerCase() as OperationId, input, signal);
  }
}
