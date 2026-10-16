import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { OidcBindingChange } from "../entities/oidc-binding-change";
import type { PlatformAccess } from "../entities/platform-access";
import { canManagePlatformAccounts } from "../policies/account-access-policy";
import { oidcBindingFailure } from "../policies/oidc-binding-policy";
import type { OidcBindingRepository } from "../repositories/oidc-binding-repository";

export class SaveOidcBinding {
  constructor(private readonly bindings: OidcBindingRepository) {}
  execute(
    access: PlatformAccess,
    operation: OperationId,
    fields: OidcBindingChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManagePlatformAccounts(access))
      return Promise.resolve(failed("platform_administrator_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_operation_id"));
    const input = Object.freeze({
      ...fields,
      accountId: fields.accountId.toLowerCase() as OidcBindingChange["accountId"],
      id: fields.id.toLowerCase() as OidcBindingChange["id"],
      reason: fields.reason.trim(),
    });
    const failure = oidcBindingFailure(input);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.bindings.save(operation.toLowerCase() as OperationId, input, signal);
  }
}
